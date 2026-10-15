#!/usr/bin/env python3
"""Check native ELF LOAD alignment inside all APK ABIs. Does not claim device execution."""
import pathlib,struct,sys,zipfile
apk=pathlib.Path(sys.argv[1]);errors=[];libraries=[]
with zipfile.ZipFile(apk) as z:
    for name in z.namelist():
        if not (name.startswith('lib/') and name.endswith('.so')):continue
        b=z.read(name)
        if b[:4]!=b'\x7fELF':errors.append(name+': not ELF');continue
        bits=b[4];endian='<' if b[5]==1 else '>'
        if bits==2:
            phoff=struct.unpack_from(endian+'Q',b,32)[0];entsize,number=struct.unpack_from(endian+'HH',b,54)
            load=[struct.unpack_from(endian+'Q',b,phoff+i*entsize+48)[0] for i in range(number) if struct.unpack_from(endian+'I',b,phoff+i*entsize)[0]==1]
        else:
            phoff=struct.unpack_from(endian+'I',b,28)[0];entsize,number=struct.unpack_from(endian+'HH',b,42)
            load=[struct.unpack_from(endian+'I',b,phoff+i*entsize+28)[0] for i in range(number) if struct.unpack_from(endian+'I',b,phoff+i*entsize)[0]==1]
        libraries.append((name,min(load) if load else 0))
        if any(a<16384 for a in load) or not load:errors.append(name+': LOAD alignment <16 KiB')
for name,alignment in libraries:print(f'{name}: LOAD {alignment}')
if errors:raise SystemExit('\n'.join(errors))
print(f'OK: {len(libraries)} native libraries; ELF LOAD alignment >=16 KiB. APK {apk.stat().st_size} bytes')
