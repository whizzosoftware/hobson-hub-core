/* globals Backbone, $, alert, _ */
$(function(){
    'use strict';

    var Header = Backbone.View.extend({
        el: '#header'
    });
    var Steps = Backbone.View.extend({
        el: '#steps',
        dom: {},
        events: {
            'click .column': 'step'
        },
        initialize: function(){
            return this.render();
        },
        render: function(){
            return this;
        },
        step: function(){
            alert('step');
        },
        setStep: function(id){
            id = id ? '#step-' + id : '#step-home';

            var $step = this.$el.find(id);
            var $active = this.$el.find('.active');

            if($step.length){
                $active.removeClass('active');
                $step.addClass('active');
            }else{
                console.log('something went wrong man!');
            }
        }
    });

    //Define steps
    
    /****** MAIN CONFIG MODEL */
    var HubConfigurationModel = Backbone.Model.extend({
        url: '/api/v1/users/local/hubs/local/configuration',
        defaults: {
            name: ''
        }
    });
    var Hub = new HubConfigurationModel();

    /****** HOME ******/
    var ImageModel = Backbone.Model.extend({
        url: '/api/v1/users/local/hubs/local/image',
        defaults: {
            image: null
        },
        isValid: function(){
            return !!this.get('image');
        }
    });
    var AddressModel = Backbone.Model.extend({
        url: function(){
            return 'http://nominatim.openstreetmap.org/search/' + encodeURIComponent(this.get('address')) + '?format=json&polygon=1&addressdetails=1';
        },
        defaults: {
            address: '',
            lat: null,
            lng: null
        },
        initialize: function(){
            console.log('initialized AddressModel');
        },
        fetch: function(){
            if(!this.get('address')){
                return false;
            }else{
                return Backbone.Model.prototype.fetch.call(this);
            }
        },
        isValid: function(){
            var lat = this.get('lat');
            var lng = this.get('lng');
            var text = this.get('address');

            return !!lat && !!lng && !!text;
        }
    });
    var Home = Backbone.View.extend({
        className: 'ui centered padded ten column wide grid form',
        id: 'home',
        tagName: 'form',
        template: _.template($('#template-home').html()),
        events: {
            'submit': 'submit',
            'change [type=file]':'uploadPicture',
            'blur #home-address': 'checkAddress'
        },
        initialize: function(){
            this.addressModel = new AddressModel();
            this.imageModel = new ImageModel();
            this.model = Hub;

            $('#template-home').remove();

            return this.render();
        },
        render: function(){
            this.$el.html(this.template());

            this.dom = {
                file: this.$el.find('input[type=file]'),
                fileWrapper: this.$el.find('#home-image-picker-wrapper .ui.field.file'),
                img: this.$el.find('#home-image-picker-preview'),
                submit: this.$el.find('button[type=submit]')
            };

            this.$el.form({
                inline: true,
                on: 'blur',
                'nickname': {
                    identifier: 'home-nickname',
                    rules: [
                        {
                            type: 'empty',
                            prompt: 'Nickname can not be empty.'
                        }
                    ]
                }
            });

            return this;
        },
        show: function(){
            this.$el.appendTo('body');

            return this;
        },
        hide: function(){
            this.$el.detach();

            return this;
        },
        submit: function(e){
            if(e && e.preventDefault){
                e.preventDefault();
            }
            var isValid = this.$el.form('validate form');
            var location = this.addressModel.get('location');

            if(!isValid){
                return;
            }

            $('body').dimmer('show');

            var save = _.bind(function(){
                if(this.addressModel.isValid()){
                    this.model.set('location', {
                        latitude: this.addressModel.get('lat'),
                        longitude: this.addressModel.get('lng'),
                        text: this.addressModel.get('address')
                    });
                }else if(location && location.text){
                    this.model.set('location', {text: location});
                }else{
                    this.model.unset('location');
                }

                this.model.set('name', this.$el.form('get value', 'home-nickname'));
                this.model.save(null, {type: 'PUT'})
                    .then(_.bind(onSave, this))
                    .fail(_.bind(onError, this))
                    .always(_.bind(onComplete, this));
            }, this);
            var onSave = function(){
                Backbone.history.navigate('plugins', {trigger: true});
            };
            var onError = function(xhr){
                if(xhr.statusText === 'Accepted'){
                    onSave();
                }else{
                    console.log('model not saved', arguments);
                }
            };
            var onImageError = _.bind(function(xhr){
                if(xhr.statusText === 'Accepted'){
                    save()
                }else{
                    $('body').dimmer('hide');
                    this.$el.addClass('error');
                }
            }, this);
            var onComplete = function(){
                this.$el.dimmer('hide');
            };

            if(this.imageModel.isValid()){
                this.$el.dimmer('show');
                this.imageModel.save(null, {type: 'PUT'}).then(save).fail(onImageError);
            }else{
                save();
            }
        },
        uploadPicture: function(e){
            var files = e.target.files || e.dataTransfer.files;
            var file = files[0];
            // Verify that we have only image file.
            if (!file.type.match('image.*')) {
                alert('Please select a JPG or PNG image');
                return;
            }

            this.dom.fileWrapper.dimmer('show');

            // Show Image Preview if FileReader supported
            if( typeof FileReader === 'undefined' )
            {
                alert('Your browser has no support for the FileReader API, we won\'t be able to display your image preview.');
            } else {
                var reader = new FileReader();
                reader.onload = _.bind(function (e) {
                    var data = e.target.result.split(',');
                    var type = data[0].split(';')[0].replace('data:', '');

                    this.dom.img.attr('src', e.target.result);
                    this.imageModel.set('image', {mediaType: type, data: data[1]});
                }, this);
                reader.readAsDataURL( file );
            }

            this.dom.fileWrapper.dimmer('hide');
            this.dom.fileWrapper.addClass('img-loaded');
        },
        checkAddress: function(){
            var $address = $(this.$el.form('get field', 'home-address'));
            var address = this.$el.form('get value', 'home-address');
            var $submit = this.dom.submit;
            var model = this.addressModel;

            var onComplete = function(){
                $submit.prop('disabled', false).removeClass('disabled');
                $address.prop('readonly', false).parent('.field').removeClass('error disabled success');
            };
            var onAddress = function(r){
                console.log(r[0]);
                $address.parent('.field').addClass('success');
                model.set('lat', parseFloat(r[0].lat, 10));
                model.set('lng', parseFloat(r[0].lon, 10));
                model.set()
            };
            var onError = function(){
                $address.parent('.field').addClass('error');
                model.set('nominatimData', null);
                model.set('valid', false);
            };

            if(!_.isEmpty(address)){
                $submit.prop('disabled', true).addClass('disabled');
                $address.prop('readonly', true).parent('.field').addClass('disabled');
                this.addressModel.set('address', address)
                    .save()
                    .then(onAddress)
                    .fail(onError)
                    .always(onComplete);
            }
        }
    });

    /****** PLUGINS ******/
    var PluginModel = Backbone.Model.extend({
        urlRoot: '/api/v1/users/:userId/hubs/local/plugins/',
        url: function(){
            return this.urlRoot + this.get('id') + '/' + this.get('currentVersion') + '/install';
        },
        defaults: {
            selected: false
        }
    });
    var PluginItem = Backbone.View.extend({
        className: 'ui items eight wide column',
        template: _.template('<div class="ui item"><div class="image"><img src="<%= data.icon %>"></div><div class="content"><h4 class="header"><%= data.name %></h4><p class="description"><%= data.description %></p></div></div>'),
        events: {
            'click': 'toggle'
        },
        initialize: function(options){
            console.log('initialize item');
            if(!options && !options.model){
                this.model = new PluginModel();
            }
        },
        render: function(){
            var data = this.model.toJSON();

            this.$el.html(this.template({data: {icon: data.links.icon, name: data.name, description: data.description}}));

            return this;
        },
        toggle: function(){
            console.log(this.model.get('selected'));
            var selected = !this.model.get('selected');

            this.model.set('selected', selected);

            this.$el.toggleClass('active', selected);
        }
    });
    var PluginsCollection = Backbone.Collection.extend({
        model: PluginModel,
        url: '/api/v1/users/local/hubs/local/plugins?remote=true&details=true'
    });
    var Plugins = Backbone.View.extend({
        fragment: null,
        className: 'ui centered padded twelve column wide grid form',
        id: 'plugins',
        template: _.template($('#template-plugins').html()),
        dom: {},
        tagName: 'form',
        events: {
            'submit': 'submit'
        },
        initialize: function(){
            this.collection = new PluginsCollection();

            this.listenTo(this.collection, 'request', this.onRequest, this);

            $('#template-plugins').remove();

            this.render();

            return this;
        },
        render: function(){
            this.$el.html(this.template());

            this.dom = {
                plugins: this.$el.find('#plugins-container')
            };

            return this;
        },
        onRequest: function(){
            $('body').dimmer('show');
        },
        onError: function(){
            $('body').dimmer('hide');
        },
        addAll: function(){
            this.fragment = document.createDocumentFragment();
            this.collection.each(this.addOne, this);

            this.dom.plugins.append(this.fragment);
            $('body').dimmer('hide');
        },
        addOne: function(model){
            var view = new PluginItem({model: model}).render();

            this.fragment.appendChild(view.$el[0]);

            return this;
        },
        show: function(){
            this.$el.appendTo('body');

            this.collection
                .fetch()
                .then(_.bind(this.addAll, this))
                .fail(_.bind(this.onError, this));

            return this;
        },
        hide: function(){
            this.$el.detach();

            return this;
        },
        submit: function(e){
            if(e && e.preventDefault){
                e.preventDefault();
            }

            var selectedPlugins = this.collection.filter(function(p){return p.get('selected')});
            var installCount = 0;

            var onInstall = function(){
                installCount++;

                if(installCount === selectedPlugins.length){
                    $('body').dimmer('hide');
                    Backbone.history.navigate('email', {trigger: true});
                }
            };
            var onError = function(xhr){
                console.log('not installed', arguments);
                if(xhr.statusText === 'Accepted'){
                    onInstall();
                }else{
                    console.log('not installed, abort');
                }
            };

            if(selectedPlugins.length) {
                _.each(selectedPlugins, function(plugin){
                    plugin.save(null, {type: 'POST'}).then(onInstall).fail(onError);
                });
            }else {
                Backbone.history.navigate('email', {trigger: true});
            }
        }
    });

    /******* EMAIL *******/
    var TestEmail = Backbone.Model.extend({
        url: '/api/v1/users/local/hubs/local/configuration/sendTestEmail',
        defaults: {
            server: '',
            secure: false,
            senderAddress: '',
            username: '',
            password: ''
        }
    });

    var Email = Backbone.View.extend({
        className: 'ui centered padded twelve column wide grid form',
        id: 'email',
        template: _.template($('#template-email').html()),
        dom: {},
        tagName: 'form',
        events: {
            'submit': 'submit',
            'change [type=radio]': 'onCheck',
            'click #email-test-button-wrapper button': 'testEmail'
        },
        initialize: function(){
            $('#template-email').remove();

            this.model = Hub;
            this.render();

            return this;
        },
        render: function(){
            this.$el.html(this.template());

            this.dom = {
                form: this.$el.find('form'),
                serverRadio: this.$el.find('#email-server .ui.radio'),
                checkbox: this.$el.find('#email-server-ssl-wrapper .ui.checkbox'),

                //Wrappers
                emailServerWrapper: this.$el.find('#email-server-name-wrapper'),
                emailServerSSLWrapper: this.$el.find('#email-server-ssl-wrapper'),
                emailUsernameWrapper: this.$el.find('#email-username-wrapper'),
                emailPasswordWrapper: this.$el.find('#email-password-wrapper'),
                emailSenderWrapper: this.$el.find('#email-sender-wrapper'),
                emailTestButtonWrapper: this.$el.find('#email-test-button-wrapper')
            };

            this.dom.serverRadio.checkbox();
            this.dom.checkbox.checkbox();
            this.$el.form({
                on:'blur'
            });

            return this;
        },
        show: function(){
            this.$el.appendTo('body');

            return this;
        },
        hide: function(){
            this.$el.detach();

            return this;
        },
        submit: function(e){
            if(e && e.preventDefault){
                e.preventDefault();
            }

            var v = this.$el.form('get values');
            var config = false, onSave, onFail, onComplete;

            switch(v['email-server-type']){
            case 'other':
                config = {
                    server: v['email-server-name'],
                    secure: this.dom.checkbox.checkbox('is checked'),
                    senderAddress: v['email-sender'],
                    username: v['email-username'],
                    password: v['email-password']
                };
                break;
            case 'gmail':
                config = {
                    server: 'smtp.gmail.com',
                    secure: true,
                    senderAddress: v['email-sender'],
                    username: v['email-username'],
                    password: v['email-password']
                };
                break;
            }

            if(!_.isEmpty(config)){
                onSave = function(){
                    console.log('saved');
                    Backbone.history.navigate('#password', {trigger: true});
                };
                onFail = function(xhr){
                    if(xhr.statusText === 'Accepted'){
                        onSave();
                    }else{
                        console.log('dhuuu error saving hub configuration!');
                    }
                };
                onComplete = function(){
                    $('body').dimmer('hide');
                };
                $('body').dimmer('show');
                this.model.set('email', config)
                    .save(null, {type: 'PUT'})
                    .then(onSave)
                    .fail(onFail)
                    .always(onComplete);
            }
        },
        onCheck: function(){
            var values = this.$el.form('get values');

            switch(values['email-server-type']){
            case 'gmail': 
                this.toggle(false); 
                this.dom.emailServerWrapper
                    .addClass('readonly')
                    .find('input')
                        .prop('readonly', true)
                        .val('smtp.gmail.com');
                this.dom.checkbox
                    .checkbox('check')
                    .checkbox('disable');

                break;
            case 'other': 
                this.toggle(false);
                this.dom.emailServerWrapper
                    .removeClass('readonly')
                    .find('input')
                        .prop('readonly', false)
                        .val('')
                this.dom.checkbox
                    .checkbox('uncheck')
                    .checkbox('enable');
                break;
            default: 
                this.toggle(true);
                this.dom.emailServerWrapper
                    .removeClass('readonly')
                    .find('input')
                        .prop('readonly', false)
                        .val('')
                this.dom.checkbox
                    .checkbox('uncheck');

                break;
            }
        },
        toggle: function(remove){
            var disabled = !!remove;

            this.dom.emailServerWrapper.toggleClass('disabled', remove).find('input').prop('disabled', disabled);
            this.dom.emailServerSSLWrapper.toggleClass('disabled', remove);
            this.dom.emailUsernameWrapper.toggleClass('disabled', remove).find('input').prop('disabled', disabled);
            this.dom.emailPasswordWrapper.toggleClass('disabled', remove).find('input').prop('disabled', disabled);
            this.dom.emailSenderWrapper.toggleClass('disabled', remove).find('input').prop('disabled', disabled);
            this.dom.emailTestButtonWrapper.toggleClass('disabled', remove).find('button').prop('disabled', disabled);

            if(disabled){
                this.dom.checkbox.checkbox('disable');
            }else{
                this.dom.checkbox.checkbox('enable');
            }
        },
        testEmail: function(){
            var v = this.$el.form('get values');

            if(v['email-server-type'] && v['email-server-name'] && v['email-sender'] && v['email-username'] && v['email-password']){
                var onTest = function(){
                    console.log('test ok', arguments);
                };
                var onError = function(){
                    console.log('test fail', arguments);
                };
                var onComplete = function(){
                    $('body').dimmer('hide');
                };

                $('body').dimmer('show');
                var model = new TestEmail({
                    server: v['email-server-name'],
                    secure: this.dom.checkbox.checkbox('is checked'),
                    senderAddress: v['email-sender'],
                    username: v['email-username'],
                    password: v['email-password']
                })
                //Save model
                model.save().then(onTest).fail(onError).always(onComplete);
            }
        }
    });

    /******* PASSWORD *******/
    var PasswordModel = Backbone.Model.extend({
        url: '/api/v1/users/local/hubs/local/configuration/password',
        defaults: {
            currentPassword: 'admin',
            newPassword: ''
        }
    });
    var PasswordView = Backbone.View.extend({
        className: 'ui centered padded twelve column wide grid form',
        id: 'password',
        template: _.template($('#template-password').html()),
        dom: {},
        tagName: 'form',
        events: {
            'submit': 'submit'
        },
        initialize: function(){
            $('#template-password').remove();

            this.model = new PasswordModel();

            this.render();

            return this;
        },
        render: function(){
            this.$el.html(this.template());

            this.$el.form({
                inline: true,
                on: 'blur',
                'password-password': {
                    identifier: 'password-password',
                    rules: [
                        {
                            type: 'empty',
                            prompt: 'Your password can not be empty'
                        },
                        {
                            type: 'length[8]',
                            prompt: 'Password must be at least 8 characters long.'
                        }
                    ]
                },
                'password-confirmation': {
                    identifier: 'password-confirmation',
                    rules: [
                        {
                            type: 'match[password-password]',
                            prompt: 'Passwords must match'
                        }
                    ]
                }
            });

            return this;
        },
        show: function(){
            this.$el.appendTo('body');

            return this;
        },
        hide: function(){
            this.$el.detach();

            return this;
        },
        submit: function(e){
            if(e && e.preventDefault){
                e.preventDefault();
            }

            var isValid = this.$el.form('validate form');
            var v, onSave, onFail, onComplete;

            if(isValid){
                v = this.$el.form('get values');
                onSave = function(){
                    console.log('saved');
                    Backbone.history.navigate('#complete', {trigger: true});
                };
                onFail = function(xhr){
                    if(xhr.statusText === 'Accepted'){
                        onSave();
                    }else{
                        console.log('error saving hub password!');
                    }
                };
                onComplete = function(){
                    $('body').dimmer('hide');
                }

                $('body').dimmer('show');
                this.model.set('newPassword', v['password-password'])
                    .save(null)
                    .then(onSave)
                    .fail(onFail)
                    .always(onComplete);
            }else{
                console.log('show validation errors');
            }
        }
    });

    /******* COMPLETE *******/
    var Complete = Backbone.View.extend({
        className: 'ui centered padded twelve column wide grid form',
        id: 'complete',
        template: _.template($('#template-complete').html()),
        dom: {},
        events: {
            'click button': 'home'
        },
        initialize: function(){
            $('#template-complete').remove();

            this.render();

            return this;
        },
        render: function(){
            this.$el.html(this.template({data: {password: 'secret'}}));

            return this;
        },
        show: function(){
            this.$el.appendTo('body');

            return this;
        },
        hide: function(){
            this.$el.detach();

            return this;
        },
        home: function(){
            window.location = '/';
        }
    });
    
    var Router = Backbone.Router.extend({
        routes: {
            ''       : 'home',
            'home'   : 'home',
            'plugins': 'plugins',
            'email'  : 'email',
            'password': 'password',
            'complete': 'complete'
        },
        views: {},
        /**
        * Overridden because of aspects.
        *
        * @overridden
        */
        _bindRoutes: function () {
            if (!this.routes) {
                return;
            }

            // Added only this method call to orginal _bindRoutes.
            // The reason of that is to decorated original route handlers.
            this.addAspectsToRoutes();

            var routes = [];
            for (var route in this.routes) {
                routes.unshift([route, this.routes[route]]);
            }
            for (var i = 0, l = routes.length; i < l; i++) {
                this.route(routes[i][0], routes[i][1], this[routes[i][1]]);
            }
        },

        /**
        * Decorate routes for different purposes, I'm trying to avoid
        * repeating ourselves for repetitive tasks, like: 
        * - killing views on route changes
        * - updating Google Analytics
        * - updating user session's timestamp
        * - bootstraping those views that require an authenticated user
        *   to be displayed
        * etc...
        */
        addAspectsToRoutes: function(){
            //Grab unique route values
            var routes = _.unique(_.values(this.routes));

            //Will close current view if any
            var beforeAll = function(){
                if (this.currentView instanceof Backbone.View) {
                    this.currentView.hide();
                    this.currentView = null;
                }
            }.bind(this);

            var afterAll = function(){
                console.log(this.currentView, 'after');
                if (this.currentView instanceof Backbone.View) {
                    this.currentView.show();
                }

                var step = Backbone.history.getFragment();
                //Highlight current step
                this.views.steps.setStep(step || '');
            }.bind(this);

            //Execute "beforeAll" function before all pages
            aspect.add(this, routes, beforeAll);
            aspect.add(this, routes, afterAll, 'after');
        },
        initialize: function(options){
            if(options && options.views){
                this.views = options.views;
            }
        },
        home: function(){
            this.currentView = this.views.home;
        },
        plugins: function(){
            this.currentView = this.views.plugins;
        },
        email: function(){
            this.currentView = this.views.email;
        },
        password: function(){
            this.currentView = this.views.password;
        },
        complete: function(){
            this.currentView = this.views.complete;
        }
    });

    //Create all views for brevity
    var header = new Header();
    var steps = new Steps();
    var home = new Home();
    var plugins = new Plugins();
    var email = new Email();
    var passwordView = new PasswordView();
    var complete = new Complete();
    //Create router
    (new Router({
        views: {
            //Steps
            home: home,
            plugins: plugins,
            email: email,
            password: passwordView,
            complete: complete,
            //UI
            header: header,
            steps: steps
        }
    }));
    //Initialize history
    Backbone.history.start(/*{pushState: true}*/);
});