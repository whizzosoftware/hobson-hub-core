// Filename: views/plugins.js
define([
    'jquery',
    'underscore',
    'backbone',
    'models/itemList',
    'services/hub',
    'views/plugin',
    'views/footer',
    'views/error',
    'i18n!nls/strings',
    'text!templates/pluginsLoading.html',
    'text!templates/plugins.html'
], function($, _, Backbone, ItemList, HubService, PluginView, FooterView, ErrorView, strings, loadingTemplate, template) {

    return Backbone.View.extend({

        loadingTemplate: _.template(loadingTemplate),

        template: _.template(template),

        events: {
            'pluginSelectionChange': 'onPluginSelectionChange',
            'next': 'onNext', // this event is fired by the footer view
            'back': 'onBack'
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
            var plugins = new ItemList({
                url: '/api/v1/users/local/hubs/local/plugins/remote?expand=item'
            });

            this.$el.append(this.loadingTemplate({strings: strings}));

            plugins.fetch({
                context: this, 
                success: function(model, response, options) {
                    console.debug(model);

                    var ctx = options.context;
                    ctx.$el.html(ctx.template({ strings: strings }));
                    ctx.$el.append(ctx.footerView.render().el);

                    // render all installable plugins
                    var row;
                    var containerEl = ctx.$el.find('#plugins-list');
                    if (model.numberOfItems() > 0) {
                        for (var i = 0; i < model.numberOfItems(); i++) {
                            if (i % 2 == 0) {
                                row = $('<div class="row"></div>');
                                containerEl.append(row);
                            }
                            var plugin = model.item(i);
                            console.debug(plugin);
                            var pluginView = new PluginView(plugin);
                            row.append(pluginView.render().el);
                            ctx.subviews.push(pluginView);
                        }
                    } else {
                        containerEl.append('<p>Sorry, there are no plugins currently available.</p>');
                    }
                },
                error: function(model, response, options) {
                    if (response.status === 401) {
                        options.context.$el.html(new ErrorView({message: strings.WizardTimeoutError}).render().el);
                    } else {
                        options.context.$el.html(new ErrorView({message: strings.WizardGenericError}).render().el);
                    }
                }
            });

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
        },

        onBack: function() {
            Backbone.history.navigate('#', {trigger: true});
        }

    });

});
