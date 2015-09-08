// Filename: views/wizard.js
define([
    'jquery',
    'underscore',
    'backbone',
    'i18n!nls/strings',
    'text!templates/wizard.html'
], function($, _, Backbone, strings, wizardTemplate) {

    return Backbone.View.extend({
        template: _.template(wizardTemplate),

        render: function() {
            this.$el.append(this.template({ strings: strings }));
            this.setActiveTab('start');
            return this;
        },

        setActiveTab: function(tab) {
            var tabName = tab + 'Tab';
            this.$el.find('.wizard-item').each(function(index, obj) {
                if (obj.id === tabName) {
                    $(obj).addClass('active shadow-2');
                } else {
                    $(obj).removeClass('active shadow-2');
                }
            });
        }

    });

});
