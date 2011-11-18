

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
  iframes: []
  constructor: (@container) -> 
    @imgs = @container.find('.page').map(-> new Box($(this), @) )
    @

  computeWeights: ->
    for img in @imgs
      img.setSize(200, 150)
    @

  computeDistribution: -> 
    # todo
    margin = 10
    x = 0
    y = 0
    maxW = @container.width()
    for img in @imgs
      if( x > maxW - 200)
        x = 0
        y += 150+margin
      img.setPosition(x, y)
      x += 200+margin
    @

  start: -> 
    @computeWeights()
    @computeDistribution()
    $(window).resize(=>
      @computeDistribution()
    )
    @container.addClass('transitionStarted')
    @

$( -> 
  new Engine($('#pages')).start()
)
