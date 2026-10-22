"""Check shipped Xposed metadata, entrypoints and compile-only stub exclusion."""
import struct
import sys
import zipfile


def classes(data):
    _, strings = struct.unpack_from('<II', data, 56)
    _, types = struct.unpack_from('<II', data, 64)
    count, definitions = struct.unpack_from('<II', data, 96)
    for index in range(count):
        type_index = struct.unpack_from('<I', data, definitions + index * 32)[0]
        string_index = struct.unpack_from('<I', data, types + type_index * 4)[0]
        offset = struct.unpack_from('<I', data, strings + string_index * 4)[0]
        while data[offset] & 128:
            offset += 1
        offset += 1
        yield data[offset:data.index(0, offset)].decode()


def verify(path):
    with zipfile.ZipFile(path) as apk:
        metadata = apk.read('META-INF/xposed/module.prop').decode()
        for value in ('minApiVersion=101', 'targetApiVersion=101'):
            assert value in metadata, value
        modern = apk.read('META-INF/xposed/java_init.list').decode().strip()
        legacy = apk.read('assets/xposed_init').decode().strip()
        names = set()
        for entry in apk.namelist():
            if entry.startswith('classes') and entry.endswith('.dex'):
                names.update(classes(apk.read(entry)))
        for entry in (modern, legacy):
            assert 'L' + entry.replace('.', '/') + ';' in names, f'Entry removed: {entry}'
        bad = [name for name in names if name.startswith(('Lio/github/libxposed/', 'Lde/robv/android/xposed/'))]
        assert not bad, f'Framework stubs bundled: {bad}'
    print('RESULT=success')


if __name__ == '__main__':
    verify(sys.argv[1])
