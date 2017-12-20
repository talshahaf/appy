import sys
import logcat
import native_hapy

print('begin')

Test = native_hapy.find_class("com/happy/MainActivity$Test")
Long = native_hapy.find_class("java/lang/Long")

method, arg_types, ret_type, static = native_hapy.get_method(Test, '', ())
print(method, arg_types, ret_type)

test = native_hapy.call(Test, method, (), ret_type, static)
print(test)

method, arg_types, ret_type, static = native_hapy.get_method(Test, 'ins_test', (Long,))
ret = native_hapy.call(test, method, (48,), ret_type, static)
print(ret)
print('end')