
window['console'] = {log: $.noop, debug: $.noop, error: $.noop} if !window['console']

class Box
  constructor: (@node, @container) -> 
    @img = @node.find('img')
    @weight = parseFloat(@node.attr('data-weight'))
    @
  updateWeight: ->
  setPosition: (@x, @y) -> 
    @node.css(top: @y, left: @x)
    @

  setGridSize: (@w, @h) ->

    @
  setSize: (w, h) -> 
    #@img.width(@w)
    @node.width(w).height(h)
    @

class Engine

  constructor: (@container) -> 
    @updateWidth()
    @unitDim = 80
    @updatePagesFromDOM()
    @

  updateWidth: ->
    @width = @container.width()


  updatePagesFromDOM: ->
    @boxes = @container.find('.page:not(.removing)').map(-> new Box($(this), @) )

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
      scaledValue = Math.floor( (( img.weight - min ) / (max - min)) * (n-1) )
      [w, h] = scales[scaledValue]
      img.setGridSize(w, h).setSize(@unitDim*w, @unitDim*h)
    @

  computeDistribution: -> 
    windowUnitWidth = Math.floor(@width / @unitDim)
    
    objs=[]
    for box in @boxes
      objs.push(box: box, placed: false, position: [0,0])

    objs.sort( (a, b) -> b.box.weight-a.box.weight )

    nextHeight = ->
      for obj in objs
        if !obj.placed
          return obj.box.h
      0

    placeLine = (xOrigin, yOrigin, maxWidth, maxHeight) ->
      # take the higher box which fits constraints
      best = null
      for obj in objs
        if !obj.placed and obj.box.w <= maxWidth and obj.box.h <= maxHeight
          if !best or ( obj.box.h > best.box.h )
            best = obj

      if best
        best.position = [xOrigin, yOrigin]
        best.placed = true
        # If it fit the height, just go right
        if best.box.h == maxHeight
          placeLine(xOrigin+best.box.w, yOrigin, maxWidth-best.box.w, maxHeight)
        else # If it's not the same height, split into two lines
          placeLine(xOrigin+best.box.w, yOrigin, maxWidth-best.box.w, best.box.h)
          placeLine(xOrigin, yOrigin+best.box.h, maxWidth, maxHeight-best.box.h)

    y = 0
    h = nextHeight()
    while(h>0)
      h = nextHeight()
      placeLine 0, y, windowUnitWidth, h
      y += h

    # Transform placements in positions
    for obj in objs
      obj.box.setPosition(@unitDim*obj.position[0], @unitDim*obj.position[1])
    @

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
    $(window).bind('resize', => 
      @updateWidth()
      @computeDistribution()
    )
    @

$( -> 

  FEEDLOOPTIME = 10000; # 10s

  engine = new Engine($('#pages')).start()
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
