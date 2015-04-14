// Filename: views/plugin.js
define([
    'jquery',
    'underscore',
    'backbone',
    'services/hub',
    'views/footer',
    'i18n!nls/strings',
    'text!templates/plugin.html'
], function($, _, Backbone, HubService, FooterView, strings, pluginTemplate) {

    var PluginView = Backbone.View.extend({
        template: _.template(pluginTemplate),

        events: {
            'click .plugin-tile': 'onTileClick'
        },

        className: 'large-6 medium-6 columns',

        selected: false,

        initialize: function(plugin) {
            this.plugin = plugin;
        },

        render: function() {
            this.$el.append(this.template({ 
                plugin: this.plugin, 
                strings: strings 
            }));
            HubService.getPluginImage(this, this.plugin.links.icon).success(function(response, b, c) {
                console.debug();
                this.$el.find('.plugin-image').html($('<img src="data:' + c.getResponseHeader('content-type') + ';base64,' + response + '" />'));
            });
            return this;
        },

        onTileClick: function(event) {
            var el = $(event.currentTarget);
            if (el.hasClass('selected')) {
                this.selected = false;
                $(event.currentTarget).removeClass('selected');
            } else {
                this.selected = true;
                $(event.currentTarget).addClass('selected');
            }
            this.$el.trigger('pluginSelectionChange');
        }
    });

    return PluginView;
});
