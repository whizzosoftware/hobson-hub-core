// Filename: views/email.js
define([
    'jquery',
    'underscore',
    'backbone',
    'toastr',
    'foundation',
    'models/emailConfiguration',
    'models/hubConfiguration',
    'services/hub',
    'views/base',
    'views/footer',
    'views/error',
    'i18n!nls/strings',
    'text!templates/email.html'
], function($, _, Backbone, toastr, Foundation, EmailConfiguration, HubConfiguration, HubService, BaseView, FooterView, ErrorView, strings, emailTemplate) {

    return BaseView.extend({
        template: _.template(emailTemplate),

        events: {
            'change input[type=radio]': 'onServerTypeChange',
            'click #testOpenButton': 'onTestOpen',
            'click #testSendButton': 'onTestSend',
            'next': 'onNext',
            'back': 'onBack'
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
                    ctx.emailServer = ctx.$el.find('#emailServer');
                    ctx.serverSecure = ctx.$el.find('#serverSecure');
                    ctx.serverUsername = ctx.$el.find('#serverUsername');
                    ctx.serverPassword = ctx.$el.find('#serverPassword');
                    ctx.emailSender = ctx.$el.find('#emailSender');

                    // set the server type
                    ctx.setServerType(model.get('values') && model.get('values').emailServer ? model.get('values').emailServer : null);
                },
                error: function(model, response, options) {
                    if (response.status === 401) {
                        options.context.$el.append(new ErrorView({message: strings.WizardTimeoutError}).render().el);
                    } else {
                        options.context.$el.append(new ErrorView({message: strings.WizardGenericError}).render().el);
                    }
                }
            });

            return this;
        },

        onServerTypeChange: function(event) {
            this.setServerType(event.target.value);
            if (event.target.value === 'gmail') {
                this.emailServer.val('smtp.gmail.com');
                this.serverSecure.prop('checked', true);
                this.serverUsername.focus();
            } else {
                this.serverSecure.prop('checked', false);
                this.emailServer.val('');
                this.emailServer.focus();
            }
        },

        onTestSend: function(event) {
            event.preventDefault();
            var testAddress = this.$el.find('#testAddress');
            var sendButton = this.$el.find('#testSendButton');

            sendButton.blur();

            var config = this.createEmailConfiguration();

            this.clearAllFormErrors();

            var error = config.validate();
            if (error) {
                this.showFormError(error.name, error.msg);
            } else {
                HubService.sendTestEmail('admin', 'password', config).
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

            this.clearAllFormErrors();

            if (this.serverType !== 'none') {
                var error = config.validate();

                if (!error) {

                    // create a new hub model object
                    var hub = new HubConfiguration({
                        id: '/api/v1/hubs/local/configuration',
                        cclass: {
                            "@id": '/api/v1/hubs/local/configurationClass'
                        },
                        values: this.createEmailConfiguration().toJSON()
                    });

                    hub.save(null, {
                        context: this,
                        error: function(model, xhr, options) {
                            if (xhr.status == 202) {
                                Backbone.history.navigate('#password', {trigger: true});
                            } else if (xhr.status == 401) {
                                Backbone.history.navigate('#', {trigger: true});
                            } else {
                                toastr.error(strings.EmailConfigSaveError);
                                options.context.footerView.showLoading(false);
                            }
                        }
                    });
                } else {
                    this.showFormError(error.name, error.msg);
                    this.footerView.showLoading(false);
                }
            } else {
                Backbone.history.navigate('#password', {trigger: true});
            }
        },

        clearAllFormErrors: function() {
            this.clearFormError('emailServer');
            this.clearFormError('emailSender');
        },

        onBack: function(event) {
            console.debug('onBack');
            Backbone.history.navigate('#plugins', {trigger: true});
        },

        setServerType: function(type) {
            type = type ? ((type === 'smtp.gmail.com') ? 'gmail' : type) : 'none';

            var disabled = (!type || type === 'none');

            this.serverType = type;

            this.$el.find('input[name=serverType][value=' + type + ']').attr('checked', 'checked');
            this.emailServer.prop('disabled', disabled);
            this.serverSecure.prop('disabled', disabled);
            this.serverUsername.prop('disabled', disabled);
            this.serverPassword.prop('disabled', disabled);
            this.emailSender.prop('disabled', disabled);

            if (disabled) {
                this.$el.find('#testOpenButton').addClass('disabled');
            } else {
                this.$el.find('#testOpenButton').removeClass('disabled');
            }

        },

        createEmailConfiguration: function() {
            var ec = new EmailConfiguration({
                emailServer: this.emailServer.val(),
                emailSecure: this.serverSecure.prop('checked'),
                emailUsername: this.serverUsername.val(),
                emailSender: this.emailSender.val()
            });
            if (this.serverPassword.val() !== '') {
                ec.set('emailPassword', this.serverPassword.val());
            }
            return ec;
        }
    });

});
