// Filename: views/start.js
define([
    'jquery',
    'underscore',
    'backbone',
    'toastr',
    'services/hub',
    'views/footer',
    'i18n!nls/strings',
    'text!templates/password.html'
], function($, _, Backbone, toastr, HubService, FooterView, strings, passwordTemplate) {

    var PasswordView = Backbone.View.extend({
        template: _.template(passwordTemplate),

        events: {
            'next': 'onNext' // this event is fired by the footer view
        },

        initialize: function() {
            this.footerView = new FooterView({previousTab: 'email', activeTab: 'password', nextTab: 'complete'}); 
        },

        remove: function() {
            this.footerView.remove();
            Backbone.View.prototype.remove.call(this);
        },

        render: function() {
            this.$el.append(this.template({ strings: strings }));
            this.$el.append(this.footerView.render().el);
            return this;
        },

        onNext: function() {
            var password = this.$el.find('#password1').val();
            var password2 = this.$el.find('#password2').val();

            this.showPasswordFieldError(false);
            this.showRepeatFieldError(false);

            if (password) {
                if (password === password2) {
                    HubService.setPassword(this, 'local', 'local', password).fail(function(response) {
                        if (response.status === 202) {
                            this.$el.trigger('passwordChange', password);
                            Backbone.history.navigate('#complete', {trigger: true});
                        } else if (response.status === 400 && response.responseJSON && response.responseJSON.errors && response.responseJSON.errors[0]) {
                            this.showPasswordFieldError(true, response.responseJSON.errors[0].message);
                            this.footerView.showLoading(false);
                        } else {
                            toastr.error(strings.PasswordGenericError);
                            // stop the loading indicator
                            this.footerView.showLoading(false);
                        }
                    });
                } else {
                    this.showRepeatFieldError(true, strings.PasswordMatchError);
                    this.footerView.showLoading(false);
                }
            } else {
                this.showPasswordFieldError(true, strings.PasswordMissingError);
                this.footerView.showLoading(false);
            }
        },

        showPasswordFieldError: function(visible, msg) {
            var error = this.$el.find('#passwordError');
            error.text(msg);
            error.css('display', visible ? 'block' : 'none');
            if (visible) {
                this.$el.find('#passwordLabel').addClass('error');
            } else {
                this.$el.find('#passwordLabel').removeClass('error');
            }
        },

        showRepeatFieldError: function(visible, msg) {
            var error = this.$el.find('#repeatError');
            error.text(msg);
            error.css('display', visible ? 'block' : 'none');
            if (visible) {
                this.$el.find('#repeatLabel').addClass('error');
            } else {
                this.$el.find('#repeatLabel').removeClass('error');
            }
        }
    });

    return PasswordView;
});
