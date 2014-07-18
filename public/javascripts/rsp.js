$(function() {

    var feed = new EventSource("/events")

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
        },
        init = function(connection) {
            connection.getOverlay("label").setLabel(connection.sourceId.substring(15) + "-" + connection.targetId.substring(15));
            connection.bind("editCompleted", function(o) {
                if (typeof console != "undefined")
                    console.log("connection edited. path is now ", o.path);
            });
        };

    $("#flowchart-demo").click(function (evt) {
        var mode = $("input[name=mode]:checked").val();
        if (mode != "add") return;

        var nodetype = $("input[name=nodetype]:checked").val();

        // Calculate the click position relative to content which is our scrollable area
        var content = $('#content')
        var x = evt.pageX + content.scrollLeft() - content.offset().left
        var y = evt.pageY + content.scrollTop() - content.offset().top

        console.log("Trying to create " + nodetype + " node at (" + x + "," + y + ")");
        $.ajax({url: "/add",
            data: {
                "nodetype": nodetype,
                "x": x,
                "y": y
            }

        })
    })

    var _addEndpoints = function(toId, sourceAnchors, targetAnchors) {
        for (var i = 0; i < sourceAnchors.length; i++) {
            var sourceUUID = toId + sourceAnchors[i];
            instance.addEndpoint(toId, sourceEndpoint, { anchor:sourceAnchors[i], uuid:sourceUUID });
        }
        for (var j = 0; j < targetAnchors.length; j++) {
            var targetUUID = toId + targetAnchors[j];
            instance.addEndpoint(toId, targetEndpoint, { anchor:targetAnchors[j], uuid:targetUUID });
        }
    };

    feed.onmessage = function(e) {
        var cfg = JSON.parse(e.data).config
        console.log(cfg)
        instance.doWhileSuspended(function() {
            if ($("#"+cfg.id).length == 0) {
                console.log("Creating new node " + cfg.id)
                // New object
                $("#flowchart-demo").append('<div class="window" id="' + cfg.id +'"></div>')

                // We offer op to three inputs or outputs depending on how many
                // the node actually has
                var inputs = ["Left", "TopLeft", "BottomLeft"].slice(0, cfg.inputs)
                var outputs = ["Right", "BottomRight", "TopRight"].slice(0, cfg.outputs)

                _addEndpoints(cfg.id, outputs, inputs);

                instance.draggable(cfg.id, {
                    grid: [20, 20],
                    containment:"parent"
                });
            }

            var node = $("#"+cfg.id)

            var displayString = ""
            if ("display" in cfg) {
                var displayFields =cfg.display.split(",").map(function (n) {
                    // Should do the trick most of the time. Obviously some edge cases
                    var value = (cfg[n] == "" || cfg[n] == " " || isNaN(cfg[n]))
                        ? ('"' + cfg[n] + '"')
                        : cfg[n]

                    return n + ": " + value
                })
                if (displayFields.length > 0) {
                    var displayString = '<p>' + displayFields.join('<br />') + '</p>'
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
                .html('<strong>' + cfg.name + '</strong>' + displayString)
        })
    }

    instance.doWhileSuspended(function() {
        // listen for new connections; initialise them the same way we initialise the connections at startup.
        instance.bind("connection", function(connInfo, originalEvent) {
            init(connInfo.connection);
        });

        // THIS DEMO ONLY USES getSelector FOR CONVENIENCE. Use your library's appropriate selector
        // method, or document.querySelectorAll:
        //jsPlumb.draggable(document.querySelectorAll(".window"), { grid: [20, 20] });

        //

        //
        // listen for clicks on connections, and offer to delete connections on click.
        //
        instance.bind("click", function(conn, originalEvent) {
            if (confirm("Delete connection from " + conn.sourceId + " to " + conn.targetId + "?"))
                jsPlumb.detach(conn);
        });

        instance.bind("connectionDrag", function(connection) {
            console.log("connection " + connection.id + " is being dragged. suspendedElement is ", connection.suspendedElement, " of type ", connection.suspendedElementType);
        });

        instance.bind("connectionDragStop", function(connection) {
            console.log("connection " + connection.id + " was dragged");
        });

        instance.bind("connectionMoved", function(params) {
            console.log("connection " + params.connection.id + " was moved");
        });
    });

    jsPlumb.fire("jsPlumbDemoLoaded", instance);

});