// Filename: views/start.js
define([
    'jquery',
    'underscore',
    'backbone',
    'toastr',
    'foundation',
    'models/emailConfiguration',
    'models/hubConfiguration',
    'services/hub',
    'views/footer',
    'i18n!nls/strings',
    'text!templates/email.html'
], function($, _, Backbone, toastr, Foundation, EmailConfiguration, HubConfiguration, HubService, FooterView, strings, emailTemplate) {

    var StartView = Backbone.View.extend({
        template: _.template(emailTemplate),

        events: {
            'change input[type=radio]': 'onServerTypeChange',
            'click #testOpenButton': 'onTestOpen',
            'click #testSendButton': 'onTestSend',
            'next': 'onNext' // this event is fired by the footer view
        },

        serverType: 'none',

        initialize: function() {
            this.footerView = new FooterView({previousTab: 'plugins', activeTab: 'email', nextTab: 'password'});
        },

        remove: function() {
            this.footerView.remove();
            Backbone.View.prototype.remove.call(this);
        },

        render: function() {
            var hub = new HubConfiguration();
            hub.fetch({
                context: this,
                success: function(model, response, options) {
                    var ctx = options.context;

                    // render templates
                    ctx.$el.append(options.context.template({ config: model.get('values'), strings: strings }));
                    ctx.$el.append(options.context.footerView.render().el);

                    // squirrel away jquery selectors for later processing
                    ctx.serverHost = ctx.$el.find('#serverHost');
                    ctx.serverSecure = ctx.$el.find('#serverSecure');
                    ctx.serverUsername = ctx.$el.find('#serverUsername');
                    ctx.serverPassword = ctx.$el.find('#serverPassword');
                    ctx.serverSender = ctx.$el.find('#serverSender');

                    // set the server type
                    ctx.setServerType(model.get('values').emailServer);
                },
                error: function(model, response, options) {
                    console.debug('Nope!');
                    options.context.$el.append('<p>A problem occurred</p>');
                }
            });

            return this;
        },

        onServerTypeChange: function(event) {
            this.setServerType(event.target.value);
            if (event.target.value === 'gmail') {
                this.serverHost.val('smtp.gmail.com');
                this.serverSecure.prop('checked', true);
                this.serverUsername.focus();
            } else {
                this.serverSecure.prop('checked', false);
                this.serverHost.val('');
                this.serverHost.focus();
            }
        },

        onTestSend: function(event) {
            event.preventDefault();
            var testAddress = this.$el.find('#testAddress');
            var sendButton = this.$el.find('#testSendButton');

            sendButton.blur();

            var config = this.createEmailConfiguration();

            var error = config.validate();
            if (error) {
                toastr.error(error);
            } else {
                HubService.sendTestEmail('local', 'local', config).
                    fail(function(response) {
                        if (response.status === 202) {
                            toastr.success(strings.TestMessageSuccessful);
                        } else {
                            toastr.error(strings.TestMessageFailure);
                        }
                    }
                );
            }
        },

        onNext: function() {
            var config = this.createEmailConfiguration();

            console.debug('next: ', this.serverType);

            if (this.serverType !== 'none') {
                var error = config.validate();

                if (!error) {

                    // create a new hub model object
                    var hub = new HubConfiguration({
                        id: '/api/v1/users/local/hubs/local/configuration',
                        cclass: {
                            "@id": '/api/v1/users/local/hubs/local/configurationClass'
                        },
                        values: this.createEmailConfiguration().toJSON()
                    });

                    console.debug('saving model: ', hub.toJSON());

                    hub.save(null, {
                        context: this,
                        error: function(model, xhr, options) {
                            if (xhr.status == 202) {
                                Backbone.history.navigate('#password', {trigger: true});
                            } else {
                                toastr.error('An error occurred saving the e-mail configuration.');
                                options.context.footerView.showLoading(false);
                            }
                        }
                    });
                } else {
                    toastr.error(error);
                    this.footerView.showLoading(false);
                }
            } else {
                Backbone.history.navigate('#password', {trigger: true});
            }
        },

        setServerType: function(type) {
            type = type ? ((type === 'smtp.gmail.com') ? 'gmail' : type) : 'none';

            var disabled = (!type || type === 'none');

            this.serverType = type;

            this.$el.find('input[name=serverType][value=' + type + ']').attr('checked', 'checked');
            this.serverHost.prop('disabled', disabled);
            this.serverSecure.prop('disabled', disabled);
            this.serverUsername.prop('disabled', disabled);
            this.serverPassword.prop('disabled', disabled);
            this.serverSender.prop('disabled', disabled);

            if (disabled) {
                this.$el.find('#testOpenButton').addClass('disabled');
            } else {
                this.$el.find('#testOpenButton').removeClass('disabled');
            }

        },

        createEmailConfiguration: function() {
            return new EmailConfiguration({
                emailServer: this.serverHost.val(),
                emailSecure: this.serverSecure.prop('checked'),
                emailUsername: this.serverUsername.val(),
                emailPassword: this.serverPassword.val(),
                emailSender: this.serverSender.val()
            });
        }
    });

    return StartView;
});
