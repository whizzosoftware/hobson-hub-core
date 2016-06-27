// Filename: models/hub.js
define([
	'backbone'
], function(Backbone) {
	var HubModel = Backbone.Model.extend({
		id: 'local',
		url: '/api/v1/hubs/local',
		getEmailServerType: function() {
			var type = 'none';
			var email = this.get('email');
			if (email && email.server) {
				switch (email.server) {
					case 'smtp.gmail.com':
						type = 'gmail';
						break;
					default:
						type = 'other';
						break;
				}
			}
			return type;
		}
	});

	return HubModel;
});
