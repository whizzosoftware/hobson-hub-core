// Filename: config.js
require.config({
    deps: [
        'main'
    ],
    paths: {
        'jquery': '../bower_components/jquery/dist/jquery',
        'underscore': '../bower_components/underscore/underscore',
        'backbone': '../bower_components/backbone/backbone',
        'foundation': '../bower_components/foundation/js/foundation',
        'foundation.reveal': '../bower_components/foundation/js/foundation/foundation.reveal',
        'toastr': '../bower_components/toastr/toastr.min',
        'ladda': '../bower_components/ladda/dist/ladda.min',
        'spin': '../bower_components/ladda/dist/spin.min',
        'dropzone': '../bower_components/dropzone/dist/dropzone-amd-module',
        'text': '../bower_components/requirejs-text/text',
        'i18n': '../bower_components/requirejs-i18n/i18n',
        'templates': 'templates'
    },
    shim: {
        'foundation': ['jquery']
    }
});
define();
