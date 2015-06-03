// Filename: views/error.js
define([
    'jquery',
    'underscore',
    'backbone',
    'i18n!nls/strings',
    'text!templates/error.html'
], function($, _, Backbone, strings, errorTemplate) {

    return Backbone.View.extend({
        template: _.template(errorTemplate),

        className: 'error-message',

        initialize: function(options) {
            this.message = options.message;
        },

        render: function() {
            this.$el.html(this.template({
                message: this.message
            }));
            return this;
        }
    });

});
