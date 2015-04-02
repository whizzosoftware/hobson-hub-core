// Filename: services/hub.js
define([
	'jquery',
], function($) {
	var HubService = {

		sendTestEmail: function(userId, hubId, model) {
			var url = '/api/v1/users/' + userId + '/hubs/' + hubId + '/configuration/sendTestEmail';
			var data = model.toJSON();
			console.debug('POSTing to URL with data: ', url, data);
			return $.ajax(url, {
				type: 'POST',
				contentType: 'application/json',
				data: JSON.stringify(data),
				dataType: 'json'
			});
		},

		setPassword: function(ctx, userId, hubId, password) {
			var url = '/api/v1/users/' + userId + '/hubs/' + hubId + '/password';
			var data = {currentPassword: 'local', newPassword: password};
			console.debug('POSTing to URL with data: ', url, data);
			return $.ajax(url, {
				context: ctx,
				type: 'POST',
				contentType: 'application/json',
				data: JSON.stringify(data),
				dataType: 'json'
			});
		},

		installPlugin: function(ctx, url) {
			return $.ajax(url, {
				type: 'POST'
			});
			console.debug('installing plugin: ', url);
		}

	};

	return HubService;
});