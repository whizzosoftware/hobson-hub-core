// Filename: models/plugins.js
define([
	'backbone',
	'models/plugin'
], function(Backbone, PluginModel) {

	var PluginsCollection = Backbone.Collection.extend({
		model: PluginModel,
		url: '/api/v1/users/local/hubs/local/plugins/remote?expand=item'
	});

	return PluginsCollection;
});