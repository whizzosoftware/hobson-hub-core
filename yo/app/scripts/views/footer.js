// Filename: views/nav.js
define([
    'jquery',
    'underscore',
    'backbone',
    'ladda',
    'i18n!nls/strings',
    'text!templates/footer.html'
], function($, _, Backbone, Ladda, strings, footerTemplate) {

    var FooterView = Backbone.View.extend({
        template: _.template(footerTemplate),

        events: {
            'click #next': 'onNext'
        },

        className: 'row',

        initialize: function(options) {
            this.previousTab = options.previousTab;
            this.activeTab = options.activeTab;
            this.nextTab = options.nextTab;
        },

        render: function() {
            this.$el.append(this.template({ previousTab: this.previousTab, activeTab: this.activeTab, nextTab: this.nextTab, strings: strings }));
            this.laddaButton = Ladda.create(this.el.querySelector('#next'));
            return this;
        },

        onNext: function(event) {
            this.laddaButton.start();
            this.$el.trigger('next');
        },

        showLoading: function(visible) {
            if (visible) {
                this.laddaButton.start();
            } else {
                this.laddaButton.stop();
            }
        }
    });

    return FooterView;
});
