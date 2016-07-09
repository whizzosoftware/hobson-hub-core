// Filename: views/start.js
define([
    'jquery',
    'backbone',
    'toastr',
    'ladda',
    'dropzone',
    'models/hub',
    'models/hubConfiguration',
    'views/base',
    'views/footer',
    'views/error',
    'i18n!nls/strings',
    'text!templates/start.html'
], function($, Backbone, toastr, Ladda, Dropzone, Hub, HubConfiguration, BaseView, FooterView, ErrorView, strings, startTemplate) {

    return BaseView.extend({
        template: _.template(startTemplate),

        events: {
            'change input#address': 'onAddressChange',
            'next': 'onNext' // this event is fired by the footer view
        },

        initialize: function() {
            this.footerView = new FooterView({previousTab: null, activeTab: 'start', nextTab: 'plugins', showBack: false});
        },

        remove: function() {
            this.footerView.remove();
            Backbone.View.prototype.remove.call(this);
        },

        render: function() {
            $.ajax('/.well-known/openid-configuration', {
                context: this,
                method: 'GET',
                success: function(data, status, xhr) {
                    console.log('got openid configuration', data);
                    // perform a login with default username/password
                    $.ajax(data.token_endpoint, {
                        context: this,
                        method: 'POST',
                        data: {
                            username: 'local',
                            password: 'local',
                            grant_type: 'password',
                            client_id: 'hobson-webconsole',
                            scope: 'openid'
                        },
                        success: function(data, status, xhr) {
                            console.log('login successful', data);
                            // set the bearer token for all subsequent AJAX requests
                            $.ajaxSetup({
                                headers: {
                                    'Authorization': 'Bearer ' + data.access_token
                                }
                            });

                            // request hub information
                            var hubConfig = new HubConfiguration();
                            hubConfig.fetch({
                                context: this,
                                success: function(model, response, options) {
                                    console.debug(model);
                                    var ctx = options.context;
                                    ctx.$el.append(ctx.template({ config: model.toJSON(), strings: strings }));
                                    ctx.$el.append(ctx.footerView.render().el);
                                    this.addressChanged = false;

                                    this.dropzone = new Dropzone('.upload-widget', {
                                        url: '/api/v1/hubs/local/image',
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
                                    if (response.status === 401) {
                                        options.context.$el.append(new ErrorView({message: strings.WizardPasswordError}).render().el);
                                    } else {
                                        options.context.$el.append(new ErrorView({message: strings.WizardGenericError}).render().el);
                                    }
                                }
                            });
                        },
                        error: function(xhr, status, error) {
                            this.$el.append(new ErrorView({message: strings.WizardPasswordError}).render().el);
                        }
                    });
                },
                error: function(model, response, options) {
                    this.$el.append(new ErrorView({message: strings.WizardPasswordError}).render().el);
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

            if (newName === '') {
                this.showFormError('name', strings.NicknameRequired);
                this.footerView.showLoading(false);
            } else {
                this.showAddressLookupFailure(false);

                if (this.addressChanged && newAddress) {
                    // flag the address as unchaged
                    this.addressChanged = false;

                    toastr.info(strings.LookingUpAddress);

                    // make call to Nominatim
                    $.ajax('https://nominatim.openstreetmap.org/search?format=json&q=' + newAddress, {context: this}).
                        done(function(data, status, jqxhr) {
                            if (data.length == 1 && data[0].lat && data[0].lon) {

                                // set the address data in the hub model
                                var addrData = data[0];

                                this.$el.find('#latitude').val(addrData.lat);
                                this.$el.find('#longitude').val(addrData.lon);

                                toastr.clear();

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
                            toastr.error(strings.LookupFailed + ': ' + error);
                        }
                    );
                } else {
                    this.updateHub();
                }
            }
        },

        showAddressLookupFailure: function(visible) {
            this.$el.find('#latlong').css('display', visible ? 'block' : 'none');
            this.$el.find('#lookupError').css('display', visible ? 'block' : 'none');
            this.$el.find('#latitude').val('');
            this.$el.find('#longitude').val('');

            if (visible) {
                this.showFormError('address', strings.AddressLookupFailure);
            } else {
                this.clearFormError('address');
            }
        },

        updateHub: function() {
            // create a new hub model object
            var hub = new HubConfiguration({
                id: '/api/v1/hubs/local/configuration',
                cclass: {
                    "@id": '/api/v1/hubs/local/configurationClass'
                },
                values: {
                    name: this.$el.find('#name').val(),
                    address: this.$el.find('#address').val()
                }
            });

            // if latitude/longitude have been set in the text field, set them -- otherwise delete them from the hub model object
            var lat = this.$el.find('#latitude').val();
            var lon = this.$el.find('#longitude').val();
            if (lat && lon) {
                hub.get('values').latitude = parseFloat(lat);
                hub.get('values').longitude = parseFloat(lon);
            }

            // save the model to the server
            hub.save(null, {
                context: this,
                error: function(model, response, options) {
                    options.context.footerView.showLoading(false);
                    if (response.status == 202) {
                        Backbone.history.navigate('#plugins', {trigger: true});
                    } else if (response.status == 401) {
                        Backbone.history.navigate('#', {trigger: true});
                    } else {
                        toastr.error(strings.ConfigurationSaveError);
                    }
                }
            });
        }

    });

});
