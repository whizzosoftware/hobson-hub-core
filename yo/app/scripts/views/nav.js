// Filename: views/nav.js
define([
    'jquery',
    'underscore',
    'backbone',
    'i18n!nls/strings',
    'text!templates/nav.html'
], function($, _, Backbone, strings, navTemplate) {

    return Backbone.View.extend({
        template: _.template(navTemplate),

        render: function() {
            this.$el.append(this.template({ strings: strings }));
            return this;
        }

    });

});
