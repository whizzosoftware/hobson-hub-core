// Filename: services/hub.js
define([
	'jquery',
], function($) {
	var HubService = {

		sendTestEmail: function(userId, hubId, model) {
			var url = '/api/v1/users/' + userId + '/hubs/' + hubId + '/configuration/sendTestEmail';
			var json = {values: model.toJSON()};
			return $.ajax(url, {
				type: 'POST',
				contentType: 'application/json',
				data: JSON.stringify(json),
				dataType: 'json'
			});
		},

		setPassword: function(ctx, userId, hubId, password) {
			var url = '/api/v1/users/' + userId + '/hubs/' + hubId + '/password';
			var data = {currentPassword: 'local', newPassword: password};
			return $.ajax(url, {
				context: ctx,
				type: 'POST',
				contentType: 'application/json',
				data: JSON.stringify(data),
				dataType: 'json'
			});
		},

		getPluginImage: function(ctx, url) {
			return $.ajax({
				context: ctx,
				url: url + '?base64=true',
				type: 'GET'
			});
		},

		installPlugin: function(ctx, url) {
			return $.ajax(url, {
				type: 'POST'
			});
		}

	};

	return HubService;
});