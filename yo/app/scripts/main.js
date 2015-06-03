// Filename: main.js
require({locale: 'root'}, [
    'jquery',
    'underscore',
    'backbone',
    'foundation',
    'router'
], function($, _, Backbone, Foundation, Router) {
    // initialize the router
    new Router();
});
define();
