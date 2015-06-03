// Filename: models/emailConfiguration.js
define([
	'backbone'
], function(Backbone) {
	var EmailConfigurationModel = Backbone.Model.extend({

		validate: function(attrs, options) {
			if (!this.get('emailServer')) {
				return 'No server hostname has been set.';
			} else if (this.get('emailUsername') && !this.get('emailPassword')) {
				return 'No password has been set for the given username.';
			} else if (!this.get('emailSender')) {
				return 'No sender address has been set.';
			}
		},

		clear: function() {
			this.unset('emailServer');
			this.unset('emailSecure');
			this.unset('emailUsername');
			this.unset('emailPassword');
			this.unset('emailSender');
		},

		nullify: function() {
			this.set('emailServer', null);
			this.set('emailSecure', null);
			this.set('emailUsername', null);
			this.set('emailPassword', null);
			this.set('emailSender', null);
		}

	});

	return EmailConfigurationModel;
});