// Filename: models/itemList.js
define([
	'backbone'
], function(Backbone) {
	return Backbone.Model.extend({
		url: function() {
			return this.get('url');
		},
		numberOfItems: function() {
			return this.get('numberOfItems');
		},
		items: function() {
			return this.get("itemListElement");
		},
		item: function(ix) {
			return this.items()[ix].item;
		}
	});
});