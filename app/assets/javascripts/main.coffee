

class Box
  constructor: (@node, @container) -> 
    @img = @node.find('img')
    @weight = parseFloat(@node.attr('data-weight'))
    @
  setPosition: (@x, @y) -> 
    transform = 'translate('+@x+'px,'+@y+'px)'
    properties = {}
    for prefix in ['-moz-', '-webkit-', '-o-']
      properties[prefix+'transform'] = transform 
    @node.css(properties)
    @

  setSize: (@w, @h) -> 
    @img.width(@w).height(@h)
    @

class Engine

  constructor: (@container, @width=800) -> 
    @imgs = @container.find('.page').map(-> new Box($(this), @) )
    @

  computeWeights: ->
    maxDim = @width
    for img in @imgs
      dim = maxDim * (img.weight*2.5)
      img.setSize(dim, dim)
    @

  computeDistribution: -> 
    # todo
    margin = 10
    x = 0
    y = 0
    heights = []
    maxW = @width
    for img in @imgs
      if( x > maxW - img.w)
        x = 0
        maxHeight = 0
        for imgH in heights
          if(imgH.h > maxHeight)
            maxHeight = imgH.h
        y += maxHeight+margin
        heights = []
      heights.push(img)
      img.setPosition(x, y)
      x += img.w+margin
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

    console.log("newPages", newPages)
    console.log("commonPages", commonPages)
    console.log("removedPages", removedPages)

    needRecomputeDistribution = newPages.length > 0 || removedPages.length > 0
    needRecomputeWeights = needRecomputeDistribution

    for href in newPages
      node = pages.find('[href='+href+']')
      @container.prepend(node)

    for href in removedPages
      node = @container.find('[href='+href+']').remove()

    for href in commonPages
      newNode = pages.find('[href='+href+']').first()
      node = @container.find('[href='+href+']').remove()
      weight = newNode.attr('data-weight')
      if node.attr('data-weight') isnt weight
        node.attr('data-weight', weight)
        needRecomputeWeights = true

    @computeWeights if needRecomputeWeights
    @computeDistribution if needRecomputeDistribution
    @

  start: -> 
    @computeWeights()
    @computeDistribution()
    setTimeout( (=> @container.addClass('transitionStarted')), 500)
    @

$( -> 

  FEEDLOOPTIME = 10000; # 10s

  engine = new Engine($('#pages')).start()
  feedIt = (onFeeded) ->
    $.get document.location.pathname, (html) ->
      pages = $(html).find("#pages")
      engine.feed(pages)
      onFeeded and onFeeded()
  feedLoop = -> setTimeout((-> feedIt(feedLoop)), FEEDLOOPTIME)
  feedLoop()
)
