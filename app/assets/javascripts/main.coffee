

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

  start: -> 
    @computeWeights()
    @computeDistribution()
    @container.addClass('transitionStarted')
    @

$( -> 
  new Engine($('#pages')).start()
)
