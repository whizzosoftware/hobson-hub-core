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

    return Backbone.View.extend({
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

});
