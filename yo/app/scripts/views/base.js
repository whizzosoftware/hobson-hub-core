// Filename: views/base.js
define([
    'jquery',
    'underscore',
    'backbone'
], function($, _, Backbone) {

    return Backbone.View.extend({
        showFormError: function(name, msg) {
            var error = this.$el.find('#' + name + 'Error');
            error.text(msg);
            error.css('display', 'block');
            this.$el.find('#' + name + 'Label').addClass('error');
        },

        clearFormError: function(name) {
            var error = this.$el.find('#' + name + 'Error');
            error.css('display', 'none');
            this.$el.find('#' + name + 'Label').removeClass('error');
        }
    });

});
