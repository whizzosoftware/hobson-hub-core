// Filename: models/hubConfiguration.js
define([
	'backbone'
], function(Backbone) {
	return Backbone.Model.extend({
		id: '/api/v1/users/local/hubs/local/configuration',
		url: '/api/v1/users/local/hubs/local/configuration'
	});
});