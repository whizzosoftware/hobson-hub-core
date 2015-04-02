// Filename: models/plugins.js
define([
	'backbone',
	'models/plugin'
], function(Backbone, PluginModel) {

	var PluginsCollection = Backbone.Collection.extend({
		model: PluginModel,
		url: '/api/v1/users/local/hubs/local/plugins?remote=true&details=true'
	});

	return PluginsCollection;
});