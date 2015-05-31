// Filename: models/emailConfiguration.js
define([
	'backbone'
], function(Backbone) {
	var EmailConfigurationModel = Backbone.Model.extend({

		validate: function(attrs, options) {
			if (!this.get('server')) {
				return 'No server hostname has been set.';
			} else if (this.get('username') && !this.get('password')) {
				return 'No password has been set for the given username.';
			} else if (!this.get('senderAddress')) {
				return 'No sender address has been set.';
			}
		},

		clear: function() {
			this.unset('server');
			this.unset('secure');
			this.unset('username');
			this.unset('password');
			this.unset('senderAddress');
		},

		nullify: function() {
			this.set('server', null);
			this.set('secure', null);
			this.set('username', null);
			this.set('password', null);
			this.set('senderAddress', null);
		}

	});

	return EmailConfigurationModel;
});