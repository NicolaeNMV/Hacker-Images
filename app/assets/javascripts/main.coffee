
window['console'] = {log: $.noop, debug: $.noop, error: $.noop} if !window['console']

# RequestAnimationFrame polyfill : https://gist.github.com/997619
requestAnimationFrame = `function(a,b){while(a--&&!(b=window["oR0msR0mozR0webkitR0r".split(0)[a]+"equestAnimationFrame"]));return b||function(a){setTimeout(a,15)}}(5)`

# pub / sub custom events : https://gist.github.com/1000193
Events = `(function(_){return{pub:function(a,b,c,d){for(d=-1,c=[].concat(_[a]);c[++d];)c[d](b)},sub:function(a,b){(_[a]||(_[a]=[])).push(b)}}})({})`

pageTmpl = _.template('<div class="page"><img src="<%= src %>" /><a class="feedback" target="_blank" href="<%= feedbackLink %>"><%= feedbackText %></a><a href="<%= href %>" target="_blank" class="caption"><%= caption %></a></div>')

class Page
  constructor: (o) ->
    $.extend(this, o)

  insert: (container)->
    node = @node = $(pageTmpl(this)).addClass('newNode')
    img = new Image()
    img.onload = ->
      setTimeout((-> node.removeClass('newNode')), 500)
    img.src = @src
    @node.appendTo(container)
    @

  update: (values)->
    hasChanged = false
    if @weight isnt values.weight
      hasChanged = true
      @weight = values.weight
    if @feedbackText isnt values.feedbackText
      @node.find('.feedback').text(@feedbackText=values.feedbackText)
    hasChanged

  remove: ->
    @node.addClass('removing')
    setTimeout((=> @node.remove()), 2000)
    @

  setGridSize: (@w, @h) -> @
  setPosition: (@x, @y) -> 
    @node.css(top: @y, left: @x)
    @

  setFontSize: (fs) ->
    @node.css('font-size', fs)
    @
  setSize: (w, h) -> 
    @node.width(w).height(h)
    @

class Engine
  constructor: (@container, @unitDim = 100, @margin = 10) -> 
    @scales = [
      [1,1],
      [2,1],
      [2,2],
      [3,2],
      [4,2],
      [3,3],
      [4,3],
      [4,4]
    ]
    @pages = []
    @
  
  start: -> 
    @container.addClass('transitionStarted')
    @updateWidth()
    @computeWeights()
    @computeDistribution()
    lastWidth = null
    $(window).bind('resize', => 
      @updateWidth()
      if lastWidth != @width
        lastWidth = @width
        @computeDistribution()
    )
    @

  updateWidth: ->
    min = _.map(@scales, (s)->s[0]).sort( (a,b) -> b-a )[0]
    units = Math.max(min, Math.floor((window.innerWidth-@margin)/@unitDim))
    @width = @unitDim*units
    @container.width(@width)

  # Compute the weight of each box and update its size
  # The weight is projected into a simplified grid
  computeWeights: ->
    n = @scales.length
    weights = _(@pages).chain().map((b) -> b.weight)
    min = weights.min().value()
    max = weights.max().value()
    for img in @pages
      scaledValue = Math.floor (n-1)*(img.weight-min)/(max-min) # Scale weights to linear [0, n-1] int range
      [w, h] = @scales[scaledValue]
      img.setGridSize(w, h).setSize(@unitDim*w-@margin, @unitDim*h-@margin)
    @

  # Algorithm trying to distribute all images on the page into the best possible arrangement (fill the gaps).
  computeDistribution: -> 
    windowUnitWidth = Math.floor(@width / @unitDim)
    objs=_.map(@pages, (box) -> box: box, w: box.w, h: box.h, placed: false, position: [0,0]).sort((a, b) -> b.w*b.h-a.w*a.h)
    
    nextHeight = -> return obj.h for obj in objs when !obj.placed

    # Try to create a line of images by consuming boxes (recursive function), the max line bounds are (w, h), it starts from (x, y)
    placeLine = (x, y, w, h) ->
      # take the higher box which fits constraints
      best = obj for obj in objs when !obj.placed and obj.w <= w and obj.h <= h and (!best or obj.h > best.h)
      if best
        best.position = [x, y]
        best.placed = true
        # If it fit the height, just go right, else split into two lines
        if best.h == h
          placeLine x+best.w, y, w-best.w, h
        else
          placeLine x+best.w, y, w-best.w, best.h
          placeLine x, y+best.h, w, h-best.h

    # distribute while there are boxes
    y = 0
    while h = nextHeight()
      placeLine 0, y, windowUnitWidth, h
      y += h

    # Transform placements in positions
    for obj in objs
      obj.box.setPosition(@unitDim*obj.position[0], @unitDim*obj.position[1]).setFontSize((0.2+obj.box.w*0.6)+'em') 
    @

  # Usage: setPages( [ { href: "http://greweb.fr/", weight: 0.15, img: "http://greweb.fr/image.png", caption: "my awesome blog" }, ... ] )
  setPages: (pages) ->
    currentHref = _.map(@pages, (box) -> box.href)
    pagesHref = _.map(pages, (p) -> p.href)
    commonPages = _.intersection(pagesHref, currentHref)
    newPages = _.difference(pagesHref, currentHref)
    removedPages = _.difference(currentHref, pagesHref)
    
    somethingHasChanged = newPages.length > 0 || removedPages.length > 0

    for href in newPages
      newPage = _.find(pages, (p)->p.href==href)
      page = new Page(newPage)
      page.insert(@container)
      @pages.push(page)
    
    for href in removedPages
      page = _.find(@pages, (p)->p.href==href)
      page.remove()
      @pages = _.without(@pages, page)
    
    for href in commonPages
      newPage = _.find(pages, (p)->p.href==href)
      page = _.find(@pages, (p)->p.href==href)
      if page.update(newPage)
        somethingHasChanged = true
    
    if somethingHasChanged
      @computeWeights()
      @computeDistribution()
    @



class Timeline
  lastUpdate: Date.now()
  x: 0
  nowArea: 30
  nowColor: '#f00'
  minWidthSeparation: 5
  maxWidthSeparation: 100
  scales: [
    { ms: 1e4, unit: '10s', strokeStyle: '#666', lineWidth: 1, height: 5  },
    { ms: 6e4, unit: '1mn', strokeStyle: '#000', lineWidth: 1, height: 5  },
    { ms: 6e5, unit:'10mn', strokeStyle: '#000', lineWidth: 1, height: 10  },
    { ms: 36e5, unit: '1h', strokeStyle: '#000', lineWidth: 2, height: 10  }
  ]

  constructor: (@node) ->
    @canvas = @node.find('canvas')
    @ctx = @canvas[0].getContext('2d')
    @handle = @node.find('.handle')
    $(window).resize(=>@updateSize())
    @canvas.bind('mousemove',(e)  => @onMouseMove(e))
    @canvas.bind('mouseup', (e) => @onMouseUp(e))
    @canvas.bind('mousedown', (e) => @onMouseDown(e))
    $(window).bind('mouseup', (e) => @dragging=null)

  start: ->
    @updateSize()
    @nextRenderFrame()
    @

  setLastUpdate: (@lastUpdate) -> @

  xToMs: (x)->
    if x <= @nowArea
      return 0
    x -= @nowArea
    x*1000
    #1000*Math.min(x*60, Math.exp((x+100)/50))

  setHandleX: (x) ->
    ms = @xToMs(x)
    today = Date.today()
    d = @lastUpdate.addMilliseconds(-ms)
    format = if today.compareTo(d)>0 then 'yyyy-MM-dd HH:mm:ss' else 'HH:mm:ss'
    text = d.toString(format)
    if ms is 0
      text = "now ("+text+")"
    if x < @nowArea
      left = @nowArea/2
    else
      left = x
    left -= 20
    @handle.text(text)
    @handle.css('left', left+'px')

  onMouseDown: (e) ->
    @dragging = { originalEvent: e }
    @setHandleX e.clientX

  onMouseUp: (e) ->
    if @dragging
      @setHandleX e.clientX
      

  onMouseMove: (e) ->
    @setHandleX e.clientX
    if @dragging
      return
      #todo

  updateSize: ->
    @canvas[0].width = window.innerWidth - 100

  nextRenderFrame: ->
    requestAnimationFrame (=> @render()), @canvas[0]

  render: ->
    @nextRenderFrame()
    c = @ctx
    w = @canvas[0].width
    h = @canvas[0].height
    c.fillStyle='white'
    c.fillRect(0,0,w,h)
    c.fillStyle=@nowColor
    c.fillRect(0, 0, @nowArea, h)
    c.fillStyle='black'
    c.fillRect(0, @x, 1, h)

$( -> 

  timeline = new Timeline($('#timeline')).start()
  engine = new Engine($('#pages')).start()

  FEEDLOOPTIME = 8000; # 8s
  feedIt = (onFeeded) ->
    $('body').addClass('feedLoading')
    $.getJSON '/current.json', (json) ->
      pages = _.map(json, (link) ->
        href: link.url
        weight: link.weight
        src: link.image
        caption: link.title
        feedbackLink: link.feedbackLink
        feedbackText: link.feedbackText
      )
      engine.setPages(pages)
      timeline.setLastUpdate(Date.now())
      $('body').removeClass('feedLoading')
      onFeeded and onFeeded()
  feedLoop = -> setTimeout((-> feedIt(feedLoop)), FEEDLOOPTIME)
  feedIt(feedLoop)
)
