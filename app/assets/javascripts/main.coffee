
window['console'] = {log: $.noop, debug: $.noop, error: $.noop} if !window['console']

pageTmpl = _.template('<a class="page" href="<%= href %>" target="blank"><img src="<%= src %>" /><span class="caption"><%= caption %></span></a>')

# To replace Box
class Page
  constructor: (@href, @weight, @src, @caption) ->

  insert: (container)->
    node = @node = $(pageTmpl(this))#.addClass('newNode')
    @node.find('img').bind('load', ->
      setTimeout((-> node.removeClass('newNode')), 500)
    )
    @node.appendTo(container)
    @

  update: (values)->
    hasChanged = false
    if @weight isnt values.weight
      hasChanged = true
      @weight = values.weight
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
    @updateWidth()
    @

  updateWidth: ->
    units = Math.floor (window.innerWidth-@margin)/@unitDim
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
      page = new Page(newPage.href, newPage.weight, newPage.src, newPage.caption)
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

  start: -> 
    @computeWeights()
    @computeDistribution()
    setTimeout( (=> @container.addClass('transitionStarted')), 500)
    lastWidth = null
    $(window).bind('resize', => 
      @updateWidth()
      if lastWidth != @width
        lastWidth = @width
        @computeDistribution()
    )
    @

$( -> 

  engine = new Engine($('#pages')).start()

  # FIXME to be deleted

  attr = $('#pages .page').map( () ->
    href: $(this).attr('href')
    weight: parseFloat($(this).attr('data-weight'))
    src: $(this).find('img').attr('src')
    caption: $(this).find('.caption').text()
  )
  $('#pages').empty()
  engine.setPages(attr)

  FEEDLOOPTIME = 10000; # 10s
  feedIt = (onFeeded) ->
    $('body').addClass('feedLoading')
    $.get document.location.pathname, (html) ->
      pages = $(html).find("#pages .page").map( () ->
        href: $(this).attr('href')
        weight: parseFloat($(this).attr('data-weight'))
        src: $(this).find('img').attr('src')
        caption: $(this).find('.caption').text()
      )
      engine.setPages(pages)
      $('body').removeClass('feedLoading')
      onFeeded and onFeeded()
  feedLoop = -> setTimeout((-> feedIt(feedLoop)), FEEDLOOPTIME)
  feedLoop()
)
