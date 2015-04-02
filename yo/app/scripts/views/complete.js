// Filename: views/start.js
define([
    'jquery',
    'underscore',
    'backbone',
    'views/footer',
    'i18n!nls/strings',
    'text!templates/complete.html'
], function($, _, Backbone, FooterView, strings, completeTemplate) {

    var CompleteView = Backbone.View.extend({
        template: _.template(completeTemplate),

        events: {
            'next': 'onNext' // this event is fired by the footer view
        },

        initialize: function(options) {
            this.footerView = new FooterView({previousTab: 'password', activeTab: 'complete', nextTab: null}); 
            this.password = options.password;
        },

        remove: function() {
            this.footerView.remove();
            Backbone.View.prototype.remove.call(this);
        },

        render: function() {
            console.debug('complete render');
            this.$el.append(this.template({ password: this.password, strings: strings }));
            this.$el.append(this.footerView.render().el);
            return this;
        },

        onNext: function() {
            window.location.replace('/console/index.html');
        }

    });

    return CompleteView;
});
