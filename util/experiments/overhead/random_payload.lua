request = function()
  wrk.headers["Connection"] = "Keep-Alive"
  param_value = math.random(1,99999)
  path = "/?x=" .. param_value
  return wrk.format("GET", path)
end
