$(function() {

    var instance = jsPlumb.getInstance({
        // default drag options
        DragOptions : { cursor: 'pointer', zIndex:2000 },
        // the overlays to decorate each connection with.  note that the label overlay uses a function to generate the label text; in this
        // case it returns the 'labelText' member that we set on each connection in the 'init' method below.
        ConnectionOverlays : [
            [ "Arrow", { location:1 } ],
            [ "Label", {
                location:0.1,
                id:"label",
                cssClass:"aLabel"
            }]
        ],
        Container:"flowchart-demo"
    });

    // this is the paint style for the connecting lines..
    var connectorPaintStyle = {
            lineWidth:4,
            strokeStyle:"#61B7CF",
            joinstyle:"round",
            outlineColor:"white",
            outlineWidth:2
        },
    // .. and this is the hover style.
        connectorHoverStyle = {
            lineWidth:4,
            strokeStyle:"#216477",
            outlineWidth:2,
            outlineColor:"white"
        },
        endpointHoverStyle = {
            fillStyle:"#216477",
            strokeStyle:"#216477"
        },
    // the definition of source endpoints (the small blue ones)
        sourceEndpoint = {
            endpoint:"Dot",
            paintStyle:{
                strokeStyle:"#7AB02C",
                fillStyle:"transparent",
                radius:7,
                lineWidth:3
            },
            isSource:true,
            connector:[ "Flowchart", { stub:[40, 60], gap:10, cornerRadius:5, alwaysRespectStubs:true } ],
            connectorStyle:connectorPaintStyle,
            hoverPaintStyle:endpointHoverStyle,
            connectorHoverStyle:connectorHoverStyle,
            dragOptions:{},
            overlays:[
                [ "Label", {
                    location:[0.5, 1.5],
                    label:"Drag",
                    cssClass:"endpointSourceLabel"
                } ]
            ]
        },
    // the definition of target endpoints (will appear when the user drags a connection)
        targetEndpoint = {
            endpoint:"Dot",
            paintStyle:{ fillStyle:"#7AB02C",radius:11 },
            hoverPaintStyle:endpointHoverStyle,
            maxConnections:-1,
            dropOptions:{ hoverClass:"hover", activeClass:"active" },
            isTarget:true,
            overlays:[
                [ "Label", { location:[0.5, -0.5], label:"Drop", cssClass:"endpointTargetLabel" } ]
            ]
        };


    var contentPosFromEvent = function(evt) {
        // Calculate the click position relative to content which is our scrollable area
        var content = $('#content');
        return {
            x: evt.pageX + content.scrollLeft() - content.offset().left,
            y: evt.pageY + content.scrollTop() - content.offset().top
        }
    };


    var deleteNode = function(id) {
        console.log("Requesting deletion of " + id);

        $.ajax({url: "/flow/nodes/" + id,
            method: "DELETE"
        });
    };

    var putNode = function(id, config) {
        $.ajax({url: "/flow/nodes/" + id,
            method: "PUT",
            processData: false,
            data: JSON.stringify(config),
            contentType: "text/json"
        })
    };

    var postNode = function(nodetype, position) {
        var px = position.x | 0
        var py = position.y | 0
        console.log("Trying to create " + nodetype + " node at (" + px + "," + py + ")");
        $.ajax({url: "/flow/nodes",
            method: "POST",
            processData: false,
            contentType: "text/json",
            data: JSON.stringify({
                "nodeType": nodetype,
                "x": String(px),
                "y": String(py)
            })
        })
    };


    /**
     * Blocking function for connecting two nodes
     * @param sourceId Source node
     * @param targetId Target node
     * @param sourceLocation Source endpoint location
     * @param targetLocation Target endpoint location
     * @returns True if server reported connection as established, false otherwise.
     */
    var postConnection = function(sourceId, targetId, sourceLocation, targetLocation) {
        console.log("Requesting connection from " + sourceId + " to " + targetId);

        var result = false ;
        $.ajax({url: "/flow/connections",
            method: "POST",
            processData: false,
            data: JSON.stringify({
                "sourceId": sourceId,
                "targetId": targetId,
                "sourceLocation": sourceLocation,
                "targetLocation": targetLocation
            }),
            contentType: "text/json",
            async: false,
            success: function(html) {
                result = true
            }
        });

        return result;
    };

    var deleteConnection = function(sourceId, targetId) {
        console.log("Requesting disconnect of " + sourceId + " from " + targetId);
        var result = false;
        $.ajax({url: "/flow/connections/" + sourceId + "/" + targetId,
            method: "DELETE",
            success: function(html) {
                result = true;
            },
            async: false
        });
        return result
    };

    var disconnect = function(sourceId, targetId) {

    };

    $("#flowchart-demo").click(function (evt) {
        var selected = $("input[name=nodetype]:checked")
        var nodetype = selected.val();
        if (nodetype == "") return;

        position = contentPosFromEvent(evt)
        postNode(nodetype, position);

        // We created an element. Flip back to move mode
        selected.parent("label")
            .removeClass("active")

        $("#movemode").prop("checked", true)
            .parent("label")
                .toggleClass("active")
    });


    var addEndpoints = function(toId, sourceAnchors, targetAnchors) {
        for (var i = 0; i < sourceAnchors.length; i++) {
            var sourceUUID = toId + sourceAnchors[i];
            instance.addEndpoint(toId, sourceEndpoint, { anchor:sourceAnchors[i], uuid:sourceUUID });
        }
        for (var j = 0; j < targetAnchors.length; j++) {
            var targetUUID = toId + targetAnchors[j];
            instance.addEndpoint(toId, targetEndpoint, { anchor:targetAnchors[j], uuid:targetUUID });
        }
    };


    var updateNodeFromConfiguration = function(cfg, newNode) {
        var node = $("#" + cfg.id);
        if (node.is('.ui-draggable-dragging')) {
            // Node is currently being dragged. Discard updates.
            // This is fine as once the drag is done the position update
            // will cause a state update. This will only leave a small
            // window for updates to cause a bit of jumping and that should
            // be it.
            return;
        }

        var displayString = "";
        if ("display" in cfg) {
            var displayFields = cfg.display.split(",").map(function (n) {
                // Should do the trick most of the time. Obviously some edge cases
                var value = (cfg[n] == "" || cfg[n] == " " || isNaN(cfg[n]))
                    ? ('"' + cfg[n] + '"') // Quote non-numbers
                    : ((cfg[n] % 1 === 0) // Check if Int
                    ? cfg[n] // Int
                    : Number(cfg[n]).toFixed(4)); // Round double

                return n + ": " + value
            });
            if (displayFields.length > 0) {
                displayString = displayFields.join('<br />');
            }
        }

        if ("active" in cfg) {
            if (cfg.active == true) {
                node.removeClass("inactive")
            } else {
                node.addClass("inactive")
            }
        }

        node.css("left", cfg.x + "px")
            .css("top", cfg.y + "px")

        node.find("strong").html(cfg.name);

        if (displayString != "") {
            node.find("p")
                .show()
                .html(displayString);
        } else {
            node.find("p")
                .hide()
        }

        if (newNode) {
            // Attach additional event listeners
            node.draggable({
                stop: function (e) {
                    pos = node.position();
                    putNode(node.attr('id'), {
                        x: String(pos.left),
                        y: String(pos.top)
                    })
                }
            })
        }
    };

    var createNewNode = function(cfg) {
        console.log("Creating new node " + cfg.id);
        // New object
        $("#flowchart-demo").append(
            '<div class="window" id="' + cfg.id + '">' +
                '<strong/>' +
                '<span class="glyphicon glyphicon-remove pull-right"></span>' +
                '<p/>'+
            '</div>');

        // We offer op to three inputs or outputs depending on how many
        // the node actually has
        var inputs = ["Left", "TopLeft", "BottomLeft"].slice(0, cfg.inputs);
        var outputs = ["Right", "BottomRight", "TopRight"].slice(0, cfg.outputs);

        $("#"+cfg.id+" span").click(function(evt) {
            // Deletion
            deleteNode(cfg.id);
        });

        instance.draggable(cfg.id, {
            grid: [20, 20],
            containment: "parent"
        });

        addEndpoints(cfg.id, outputs, inputs);

    };

    var doesExist = function(id) {
        return $("#" + id).length > 0
    };

    var connections = {}; // sourceId + targetId -> connection

    instance.doWhileSuspended(function() {
        // listen for new connections; initialise them the same way we initialise the connections at startup.
        instance.bind("connection", function(conn, originalEvent) {
            with (conn) {
                var k = conn.sourceId + conn.targetId
                if (!(k in connections)) {
                    // New local connection yet to be propagates
                    postConnection(sourceId, targetId,
                        sourceEndpoint.anchor.type,
                        targetEndpoint.anchor.type);

                    connections[sourceId + targetId] = conn.connection
                }
            }
        });

        instance.bind("click", function(conn, originalEvent) {
            instance.detach(conn);
        });

        instance.bind("beforeDetach", function(conn) {
            with (conn) {
                if (conn.sourceId + conn.targetId in connections) {
                    // Ok. This is the user disconnecting this.
                    deleteConnection(sourceId, targetId);
                    delete connections[sourceId + targetId];
                }
            }
            return true;
        })

        instance.bind("beforeDrop", function(conn) {
           with (conn) {
               // Make sure we don't allow more than one connection for the same id pair
               if (conn.sourceId + conn.targetId in connections) {
                   console.log("User tried to connect already connected elements. Denied.");
                   return false;
               }
           }
            return true;
        });

    });

    jsPlumb.fire("jsPlumbDemoLoaded", instance);

    var deleteAllNodes = function() {
        instance.doWhileSuspended(function () {
            $(".window").each(function (idx, obj) {
                instance.remove(obj);
            });
        });
        connections = {};
    };

    $("#resetbutton").click(function() {
        deleteAllNodes();
        $.ajax('flow/reset');
    });

    var guiDisconnect = function(sourceId, targetId) {
        // Disconnect
        var k = sourceId + targetId;
        if (k in connections) {
            console.log("Remote deletion of " + sourceId + " to " + targetId + " connection");
            var conn = connections[k];
            delete connections[k];
            instance.detach(conn);
        }
    }

    var feed = new EventSource("/flow/events");
    feed.onerror = function(e) {
        // Lost connection, wipe slate clean. We'll be restored on reconnect
        deleteAllNodes();
    };

    feed.onmessage = function(e) {
        var data = JSON.parse(e.data);
        if (data.deleted == true) {
            if ("id" in data) {
                // Element deletion
                instance.doWhileSuspended(function () {
                    instance.remove(data.id);
                });
            } else {
                guiDisconnect(data.sourceId, data.targetId)
            }

            return;
        }

        if ("id" in data && "config" in data) {
            var cfg = data.config;

            instance.doWhileSuspended(function () {
                var newNode = false;
                if (!doesExist(cfg.id)) {
                    createNewNode(cfg);
                    newNode = true;
                }
                updateNodeFromConfiguration(cfg, newNode);
            })
        } else {
            // Connection data
            var cfg = data.config;
            var k = cfg.sourceId + cfg.targetId;

            var updateVisual = function(k) {
                // Update connection state
                var connection = connections[k];
                connection.getOverlay("label").setLabel(
                    cfg[cfg.display]
                );
            };

            if (!(k in connections)) {
                // New connection
                console.log("Remote connection of " + cfg.sourceId + " to " + cfg.targetId);

                var createConnection = function(tries) {
                    if (k in connections) {
                        return; // Other message beat us to it
                    }

                    if (tries == 0) {
                        console.log("Finally failed to create " + cfg.sourceId + " to " + cfg.targetId + "connection");
                        return;
                    }

                    connections[k] = null; // Prevent us from sending this as a new connection

                    if ($("#"+cfg.sourceId).length > 0 && $("#"+cfg.targetId).length > 0) {
                        // Both endpoints exist
                        var result = instance.connect({uuids: [
                                cfg.sourceId + cfg.sourceLocation,
                                cfg.targetId + cfg.targetLocation]
                        });

                        if (result == undefined || result == null) {
                            console.log("Failed to establish connection from " + cfg.sourceId + " to " + cfg.targetId + ". Stuff might break.");
                            delete connections[k];
                            return;
                        }
                    } else {
                        // Try again later
                        console.log(cfg.sourceId + " or " + cfg.targetId + " does not exist (yet), will retry connection later");
                        setTimeout(function() { createConnection(tries - 1) }, 500);
                        delete connections[k];
                        return;
                    }

                    connections[k] = result;
                    updateVisual(k);
                };

                createConnection(5);
            } else {
                updateVisual(k);
            }

        }
    };

});