// Filename: views/start.js
define([
    'jquery',
    'underscore',
    'backbone',
    'models/plugins',
    'services/hub',
    'views/plugin',
    'views/footer',
    'i18n!nls/strings',
    'text!templates/plugins.html'
], function($, _, Backbone, PluginsCollection, HubService, PluginView, FooterView, strings, pluginsTemplate) {

    var PluginsView = Backbone.View.extend({
        template: _.template(pluginsTemplate),

        events: {
            'pluginSelectionChange': 'onPluginSelectionChange',
            'next': 'onNext' // this event is fired by the footer view
        },

        subviews: [],

        initialize: function() {
            this.footerView = new FooterView({previousTab: 'start', activeTab: 'plugins', nextTab: 'email'}); 
        },

        remove: function() {
            this.footerView.remove();
            Backbone.View.prototype.remove.call(this);
        },

        render: function() {
            var plugins = new PluginsCollection();
            plugins.fetch({
                context: this, 
                success: function(model, response, options) {
                    var ctx = options.context;
                    ctx.$el.append(ctx.template({ strings: strings }));
                    ctx.$el.append(ctx.footerView.render().el);

                    // filter full plugin list to just installable ones
                    var installablePlugins  = model.filter(function(plugin) {
                        return (plugin.get('links').install);
                    });

                    // render all installable plugins
                    var row;
                    var containerEl = ctx.$el.find('#plugins-list');
                    if (installablePlugins.length > 0) {
                        for (var i = 0; i < installablePlugins.length; i++) {
                            if (i % 2 == 0) {
                                row = $('<div class="row"></div>');
                                containerEl.append(row);
                            }
                            var plugin = installablePlugins[i];
                            var pluginView = new PluginView(plugin.toJSON());
                            row.append(pluginView.render().el);
                            ctx.subviews.push(pluginView);
                        }
                    } else {
                        containerEl.append('<p>Sorry, there are no plugins currently available.</p>');
                    }
                },
                failure: function() {
                    console.debug('Nope!');
                }
            })
            return this;
        },

        onPluginSelectionChange: function() {
            var count = 0;
            for (var i=0; i < this.subviews.length; i++) {
                var pluginView = this.subviews[i];
                if (pluginView.selected) {
                    count++;
                }
            }
            this.$el.find('#installCount').text(count);
        },

        onNext: function() {
            for (var i=0; i < this.subviews.length; i++) {
                var pluginView = this.subviews[i];
                if (pluginView.selected) {
                    HubService.installPlugin(this, pluginView.plugin.links.install);
                }
            }
            Backbone.history.navigate('#email', {trigger: true});
        }

    });

    return PluginsView;
});
