import sys
import logcat
import native_hapy

print('begin')

MainActivity = native_hapy.find_class("com/happy/MainActivity$Test")
Long = native_hapy.find_class("java/lang/Long")

method, arg_types, ret_type = native_hapy.get_method(MainActivity, '', ())
print(method, arg_types, ret_type)

ret = native_hapy.call_static(MainActivity, method, (), ret_type)
print(ret)

print('end')