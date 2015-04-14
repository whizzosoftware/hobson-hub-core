// Filename: views/start.js
define([
    'jquery',
    'backbone',
    'toastr',
    'ladda',
    'dropzone',
    'models/hub',
    'views/footer',
    'views/error',
    'i18n!nls/strings',
    'text!templates/start.html'
], function($, Backbone, toastr, Ladda, Dropzone, HubModel, FooterView, ErrorView, strings, startTemplate) {

    var StartView = Backbone.View.extend({
        template: _.template(startTemplate),

        events: {
            'change input#address': 'onAddressChange',
            'next': 'onNext' // this event is fired by the footer view
        },

        initialize: function() {
            this.footerView = new FooterView({previousTab: null, activeTab: 'start', nextTab: 'plugins'}); 
        },

        remove: function() {
            this.footerView.remove();
            Backbone.View.prototype.remove.call(this);
        },

        render: function() {
            var hub = new HubModel();
            hub.fetch({
                context: this,
                success: function(model, response, options) {
                    var ctx = options.context;
                    ctx.$el.append(ctx.template({ hub: model.toJSON(), strings: strings }));
                    ctx.$el.append(ctx.footerView.render().el);
                    this.addressChanged = false;

                    this.dropzone = new Dropzone('.upload-widget', { 
                        url: '/api/v1/users/local/hubs/local/image', 
                        method: 'put',
                        maxFiles: 1,
                        acceptedFiles: 'image/jpeg,image/png',
                        previewTemplate: '<div class="dz-preview dz-file-preview"><div class="dz-details"><img data-dz-thumbnail /></div><div class="dz-progress"><span class="dz-upload" data-dz-uploadprogress></span></div><div style="display: none;" class="dz-success-mark"><span><i class="fa fa-check-circle-o"></i></span></div><div style="display: none;" class="dz-error-mark"><span><i class="fa fa-times-circle-o"></i></span></div></div>'
                    });
                    this.dropzone.on('complete', function() {
                        $('#upload-prompt').hide();
                    });
                    this.dropzone.on('success', function() {
                        $('.dz-success-mark').css('display', 'block');
                        toastr.success('Image successfully uploaded.');
                    });
                    this.dropzone.on('error', function(a, error, response) {
                        $('.dz-error-mark').css('display', 'block');
                        toastr.error(error.errors[0].message);
                    });
                },
                error: function(model, response, options) {
                    console.debug('nope: ', response);
                    if (response.status === 418) {
                        options.context.$el.append(new ErrorView({message: strings.WizardPasswordError}).render().el);
                    } else {
                        options.context.$el.append(new ErrorView({message: strings.WizardGenericError}).render().el);
                    }
                }
            });

            return this;
        },

        onAddressChange: function() {
            this.addressChanged = true;
        },

        onNext: function() {
            var newName = this.$el.find('#name').val();
            var newAddress = this.$el.find('#address').val();

            this.showAddressLookupFailure(false);

            if (this.addressChanged && newAddress) {
                // flag the address as unchaged
                this.addressChanged = false;

                // make call to Nominatim
                $.ajax('https://nominatim.openstreetmap.org/search?format=json&q=' + newAddress, {context: this}).
                    done(function(data, status, jqxhr) {
                        if (data.length == 1 && data[0].lat && data[0].lon) {

                            // set the address data in the hub model
                            var addrData = data[0];

                            this.$el.find('#latitude').val(addrData.lat);
                            this.$el.find('#longitude').val(addrData.lon);

                            // update hub information
                            this.updateHub();
                        } else {
                            // stop the loading indicator
                            this.footerView.showLoading(false);

                            // show the lat/long fields
                            this.showAddressLookupFailure(true);
                        }
                    }).
                    fail(function(jqXHR, status, error) {
                        this.footerView.showLoading(false);
                        toastr.error('Lookup failed: ' + error);
                    }
                );
            } else {
                this.updateHub();
            }
        },

        showAddressLookupFailure: function(visible) {
            this.$el.find('#latlong').css('display', visible ? 'block' : 'none');
            this.$el.find('#lookupError').css('display', visible ? 'block' : 'none');
            this.$el.find('#latitude').val('');
            this.$el.find('#longitude').val('');
            if (visible) {
                this.$el.find('#addressLabel').addClass('error');
                this.$el.find('#address').addClass('error');
            } else {
                this.$el.find('#addressLabel').removeClass('error');
                this.$el.find('#address').removeClass('error');
            }
        },

        updateHub: function() {
            // create a new hub model object
            var hub = new HubModel({
                id: 'local',
                name: this.$el.find('#name').val(),
                location: {
                    text: this.$el.find('#address').val()
                }
            });

            // if latitude/longitude have been set in the text field, set them -- otherwise delete them from the hub model object
            var lat = this.$el.find('#latitude').val();
            var lon = this.$el.find('#longitude').val();
            if (lat && lon) {
                hub.get('location').latitude = parseFloat(lat);
                hub.get('location').longitude = parseFloat(lon);
            }

            // save the model to the server
            hub.save(null, {
                context: this,
                error: function(model, xhr, options) {
                    options.context.footerView.showLoading(false);
                    if (xhr.status == 202) {
                        Backbone.history.navigate('#plugins', {trigger: true});
                    } else {
                        toastr.error('Error saving configuration. Check that your Hobson Hub is running.');
                    }
                }
            });
        }

    });

    return StartView;
});
