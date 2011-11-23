
window['console'] = {log: $.noop, debug: $.noop, error: $.noop} if !window['console']

class Box
  constructor: (@node, @container) -> 
    @img = @node.find('img')
    # to be deleted (don't read the DOM but write it)
    @weight = parseFloat(@node.attr('data-weight'))
    @
  updateWeight: ->
  setPosition: (@x, @y) -> 
    @node.css(top: @y, left: @x)
    @

  setGridSize: (@w, @h) ->

    @
  setFontSize: (fs) ->
    @node.css('font-size', fs)
    @
  setSize: (w, h) -> 
    #@img.width(@w)
    @node.width(w).height(h)
    @

class Engine
  constructor: (@container, @unitDim = 100, @margin = 10) -> 
    @updateWidth()
    @updatePagesFromDOM()
    @

  updateWidth: ->
    units = Math.floor((window.innerWidth-@margin)/@unitDim)
    @width = @unitDim*units
    @container.width(@width)

  # to be deleted...
  updatePagesFromDOM: ->
    @boxes = @container.find('.page:not(.removing)').map(-> new Box($(this), @) )

  # Compute the weight of each box and update its size
  # The weight is projected into a simplified grid
  computeWeights: ->
    min = Infinity
    max = -Infinity
    scales = [
      [1,1],
      [2,1],
      [2,2],
      [3,2],
      [4,2],
      [3,3],
      [4,3],
      [4,4]
    ]
    n = scales.length
    for img in @boxes
      if img.weight > max
        max = img.weight
      if img.weight < min
        min = img.weight

    for img in @boxes
      scaledValue = Math.floor( (( img.weight - min ) / (max - min)) * (n-1) ) # Scale weights to linear [0, n-1] int range
      [w, h] = scales[scaledValue]
      img.setGridSize(w, h).setSize(@unitDim*w-@margin, @unitDim*h-@margin)
    @

  # Algorithm trying to distribute all images on the page into the best possible arrangement (fill the gaps).
  computeDistribution: -> 
    windowUnitWidth = Math.floor(@width / @unitDim)
    
    objs=[]
    for box in @boxes
      objs.push(box: box, w: box.w, h: box.h, placed: false, position: [0,0])

    objs.sort( (a, b) -> b.w*b.h-a.w*a.h )

    nextHeight = ->
      for obj in objs
        if !obj.placed
          return obj.h
      0

    # Try to create a line of images by consuming boxes (recursive function)
    # The max line bounds are (maxWidth, maxHeight) 
    # It starts from (xOrigin, yOrigin)
    placeLine = (xOrigin, yOrigin, maxWidth, maxHeight) ->
      # take the higher box which fits constraints
      best = null
      for obj in objs
        if !obj.placed and obj.w <= maxWidth and obj.h <= maxHeight
          if !best or ( obj.h > best.h )
            best = obj

      if best
        best.position = [xOrigin, yOrigin]
        best.placed = true
        # If it fit the height, just go right
        if best.h == maxHeight
          placeLine(xOrigin+best.w, yOrigin, maxWidth-best.w, maxHeight)
        else # If it's not the same height, split into two lines
          placeLine(xOrigin+best.w, yOrigin, maxWidth-best.w, best.h)
          placeLine(xOrigin, yOrigin+best.h, maxWidth, maxHeight-best.h)

    y = 0
    h = nextHeight()
    while(h>0)
      h = nextHeight()
      placeLine 0, y, windowUnitWidth, h
      y += h

    # Transform placements in positions
    for obj in objs
      obj.box.setPosition(@unitDim*obj.position[0], @unitDim*obj.position[1])
      obj.box.setFontSize((0.2+obj.box.w*0.6)+'em')
    @

  # Usage: setPages( [ { href: "http://greweb.fr/", weight: 0.15, caption: "my awesome blog", img: "http://greweb.fr/image.png" }, ... ] )
  setPages: (pages) ->



  feed: (pages) ->
    newPages = []
    commonPages = []
    removedPages = @container.find('.page').map(-> $(this).attr('href') )
    $(pages).find('.page').map(-> $(this).attr('href')).each( (i, href) -> 
      found = false
      for i in [0..removedPages.length]
        if(removedPages[i] == href)
          commonPages.push(href)
          removedPages[i] = undefined
          found = true
          break
      if not found
        newPages.push(href)
    )
    tmp = removedPages
    removedPages = []
    removedPages.push(p) for p in tmp if p isnt undefined

    somethingHasChanged = newPages.length > 0 || removedPages.length > 0

    for href in newPages
      node = pages.find('[href="'+href+'"]')
      node.addClass('newNode')
      @container.append(node)
      node.find('img').bind('load', ->
        setTimeout((-> node.removeClass('newNode')), 500)
      )

    for href in removedPages
      node = @container.find('[href="'+href+'"]')
      node.addClass('removing')
      setTimeout((-> node.remove()), 2000) # TODO : animate remove

    for href in commonPages
      newNode = pages.find('[href="'+href+'"]')
      node = @container.find('[href="'+href+'"]')
      weight = newNode.attr('data-weight')
      currentWeight = node.attr('data-weight')
      if currentWeight isnt weight
        node.attr('data-weight', weight)
        somethingHasChanged = true
    
    if somethingHasChanged
      @updatePagesFromDOM()
      @computeWeights()
      @computeDistribution()
    @

  start: -> 
    @computeWeights()
    @computeDistribution()
    setTimeout( (=> @container.addClass('transitionStarted')), 500)
    # FIXME: don't update each time...
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
  FEEDLOOPTIME = 10000; # 10s
  feedIt = (onFeeded) ->
    $('body').addClass('feedLoading')
    $.get document.location.pathname, (html) ->
      pages = $(html).find("#pages")
      engine.feed(pages)
      $('body').removeClass('feedLoading')
      onFeeded and onFeeded()
  feedLoop = -> setTimeout((-> feedIt(feedLoop)), FEEDLOOPTIME)
  feedLoop()
)

### 
      TODO
      $(html).find("#pages .page").map(-> 
        href: $(this).attr('href'),
        weight: $(this).attr('data-weight'),
        image: $(this).find('img').attr('src'),
        caption: $(this).find('.caption').text()
      ) 
###

