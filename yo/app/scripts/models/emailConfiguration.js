// Filename: models/emailConfiguration.js
define([
	'backbone'
], function(Backbone) {
	var EmailConfigurationModel = Backbone.Model.extend({

		validate: function(attrs, options) {
			if (!this.get('emailServer')) {
				return {
					name: 'emailServer',
					msg: 'No server hostname has been set.'
				};
			} else if (!this.get('emailSender')) {
				return {
					name: 'emailSender',
					msg: 'No sender address has been set.'
				};
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