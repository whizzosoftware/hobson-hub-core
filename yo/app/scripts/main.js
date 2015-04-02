// Filename: main.js
require({locale: 'root'}, [
    'jquery',
    'underscore',
    'backbone',
    'foundation',
    'router'
], function($, _, Backbone, Foundation, Router) {
	$.ajaxSetup({
		headers: {
			'Authorization': 'Basic bG9jYWw6bG9jYWw=',
			'X-StatusOnLoginFail': 418
		}
	});

    // initialize the router
    new Router();
});
define();
