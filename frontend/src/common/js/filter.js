const options = function (value, array) {
  if (!value) return '';
  if (array) {
    for (let i = 0; i < array.length; i++) {
      if (value === array[i].key) {
        return array[i].value
      }
    }
  }
  return value;
};

const timestampFormatDate = function (timestamp) {
  if (!timestamp) {
    return timestamp
  }

  let date = new Date(timestamp);

  let y = date.getFullYear();

  let MM = date.getMonth() + 1;
  MM = MM < 10 ? ('0' + MM) : MM;

  let d = date.getDate();
  d = d < 10 ? ('0' + d) : d;

  let h = date.getHours();
  h = h < 10 ? ('0' + h) : h;

  let m = date.getMinutes();
  m = m < 10 ? ('0' + m) : m;

  let s = date.getSeconds();
  s = s < 10 ? ('0' + s) : s;

  return y + '-' + MM + '-' + d + ' ' + h + ':' + m + ':' + s
};

const timestampFormatDayDate = function (timestamp) {
  if (!timestamp) {
    return timestamp
  }

  let date = new Date(timestamp);

  let y = date.getFullYear();

  let MM = date.getMonth() + 1;
  MM = MM < 10 ? ('0' + MM) : MM;

  let d = date.getDate();
  d = d < 10 ? ('0' + d) : d;

  return y + '-' + MM + '-' + d;
};

const filters = {
  "options": options,
  "timestampFormatDate": timestampFormatDate,
  "timestampFormatDayDate": timestampFormatDayDate,
};

export default {
  install(Vue) {
    // 注册公用过滤器
    Object.keys(filters).forEach(key => {
      Vue.filter(key, filters[key])
    });
  }
}
