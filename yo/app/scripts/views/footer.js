// Filename: views/footer.js
define([
    'jquery',
    'underscore',
    'backbone',
    'ladda',
    'i18n!nls/strings',
    'text!templates/footer.html'
], function($, _, Backbone, Ladda, strings, footerTemplate) {

    return Backbone.View.extend({
        template: _.template(footerTemplate),

        events: {
            'click #next': 'onNext',
            'click #back': 'onBack'
        },

        className: 'row',

        initialize: function(options) {
            this.previousTab = options.previousTab;
            this.activeTab = options.activeTab;
            this.nextTab = options.nextTab;
            this.showBack = (typeof options.showBack !== 'undefined') ? options.showBack : true;
        },

        render: function() {
            this.$el.append(this.template({ previousTab: this.previousTab, activeTab: this.activeTab, nextTab: this.nextTab, showBack: this.showBack, strings: strings }));
            this.laddaButton = Ladda.create(this.el.querySelector('#next'));
            return this;
        },

        onNext: function(event) {
            this.laddaButton.start();
            this.$el.trigger('next');
        },

        onBack: function(event) {
            this.$el.trigger('back');
        },

        showLoading: function(visible) {
            if (visible) {
                this.laddaButton.start();
            } else {
                this.laddaButton.stop();
            }
        }
    });

});
