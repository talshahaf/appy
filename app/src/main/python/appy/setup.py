from setuptools import setup, find_packages

version = {}
with open("appy/__version__.py") as fp:
    exec(fp.read(), version)

setup(
    name='appy',
    version=version['__version__'],
    url='http://example.com/',
    author='Flying Circus',
    author_email='flyingcircus@example.com',
    license='',
    packages=find_packages(),
    long_description=open('README.txt').read(),
)