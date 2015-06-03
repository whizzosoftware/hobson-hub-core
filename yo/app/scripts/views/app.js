// Filename: views/app.js
define([
	'jquery',
	'underscore',
	'backbone',
	'views/nav',
	'views/wizard',
	'views/footer',
	'views/start',
	'views/plugins',
	'views/email',
	'views/password',
	'views/complete',
	'i18n!nls/strings',
	'text!templates/app.html'
], function($, _, Backbone, NavView, WizardView, FooterView, StartView, PluginsView, EmailView, PasswordView, CompleteView, strings, appTemplate) {

	return Backbone.View.extend({
		el: $('body'),

		template: _.template(appTemplate),

		events: {
			'passwordChange': 'onPasswordChange'
		},

		password: 'local',

		render: function() {
			this.$el.prepend(this.template());
			this.navbarView = new NavView();
			this.$el.find('#nav-container').empty().append(this.navbarView.render().el);
			this.wizardView = new WizardView();
			this.$el.find('#wizard-container').empty().append(this.wizardView.render().el);
			return this;
		},

		showStart: function() {
			this.renderContentView(new StartView(), 'start');
			this.wizardView.setActiveTab('start');
		},

		showPlugins: function() {
			this.renderContentView(new PluginsView(), 'plugins');
			this.wizardView.setActiveTab('plugins');
		},

		showEmail: function() {
			this.renderContentView(new EmailView(), 'email');
			this.wizardView.setActiveTab('email');
		},

		showPassword: function() {
			this.renderContentView(new PasswordView(), 'password');
			this.wizardView.setActiveTab('password');
		},

		showComplete: function() {
			this.renderContentView(new CompleteView({'password': this.password}), 'complete');
			this.wizardView.setActiveTab('complete');
		},

		renderContentView: function(view, activeTab) {
			if (this.contentView) {
				this.contentView.remove();
			}
			this.contentView = view;
			this.$el.find('#content-container').append(view.render().el);
		},

		onPasswordChange: function(event, password) {
			this.password = password;
		}
	});

});