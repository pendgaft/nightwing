#!/usr/bin/python

import io,re,os,string

# url example
# http://as-rank.caida.org/?as=XXX&mode0=as-info&mode1=as-rank-search

# row flag - contains
# <td style="text-align:center;" rowspan='1' class=' highlight_selected'>
# regex "<td *+</td> X2 (second is ASN) X6 (ips)

rawIPDict = {}
netIPDict = {}
asRelDict = {}

REL_FILE = "as-rel.txt"
OUT_FILE = "ip-count.csv"
TEMP_FILE = "junk.txt"
URL_FRONT = "\"http://as-rank.caida.org/?as="
URL_BACK = "&mode0=as-info&mode1=as-rank-search\""
REG_FLAG = """<td style="text-align:center;" rowspan='1' class=' highlight_selected'"""

# load asns from rel file into set
print "starting parse of rel file for set of ASNs"
relBuff = open(REL_FILE, "r")
for relLine in relBuff:
    match = re.search("(\\d+)\\|(\\d+)\\|(.+)", relLine)
    if match:
        lhs = int(match.group(1))
        rhs = int(match.group(2))
        rel = int(match.group(3))
        if not lhs in asRelDict:
            asRelDict[lhs] = []
        if not rhs in asRelDict:
            asRelDict[rhs] = []
        if rel == -1:
            asRelDict[lhs].append(rhs)
        elif rel == 1:
            asRelDict[rhs].append(lhs)
relBuff.close()
print "done building AS set, size is: " + str(len(asRelDict))

#populate rawIPDict
progress = 0
step = len(asRelDict) / 100
for asn in iter(asRelDict):
    asRelDict[asn] = set(asRelDict[asn])
    tempUrlString = URL_FRONT + str(asn) + URL_BACK
    os.system("curl -s -o " + TEMP_FILE + " " + tempUrlString)
    tempBuffer = open(TEMP_FILE, "r")
    for tempLine in tempBuffer:
        if tempLine.find(REG_FLAG) == -1:
            continue
        match = re.search("(<td .+?>.+?</td>){5}<td .+?>(.+?)</td>", tempLine)
        ipCountStr = match.group(2)
        rawIPDict[asn] = int(ipCountStr.replace(",",""))
    tempBuffer.close()
    progress = progress + 1
    if progress % step == 0:
        print str(progress/step) + "% done"

print "done parsing raw ip counts size is " + str(len(rawIPDict))

#dump raw ip to file (sanity)
rawBuff = open("raw-ip.csv", "w")
for asn in iter(rawIPDict):
    rawBuff.write(str(asn) + "," + str(rawIPDict[asn]) + "\n")
rawBuff.close()

#clone to mod IP dict
for asn in iter(rawIPDict):
    netIPDict[asn] = rawIPDict[asn]

#adjust out customer IPs
visited = set([])
while len(visited) != len(netIPDict):
    print "visited size is " + str(len(visited))
    for consider in iter(netIPDict):
        if consider in visited:
            continue
        childrenSet = set([])
        childVisited = set([])
        for tas in asRelDict[consider]:
            childrenSet.add(tas)
        while len(childrenSet) > 0:
            tas = childrenSet.pop()
            childVisited.add(tas)
            for tchild in asRelDict[tas]:
                if tchild not in childVisited:
                    childrenSet.add(tchild)
        ready = 1
        for tas in childVisited:
            if not tas in visited:
                ready = 0
                break
        if ready == 1:
            ipcount = 0
            for tchild in childVisited:
                ipcount = ipcount + netIPDict[tchild]
            netIPDict[consider] = rawIPDict[consider] - ipcount
            visited.add(consider)

for tas in iter(netIPDict):
    if netIPDict[tas] < 0:
        print "low " + tas
        netIPDict[tas] = 0

#output netIPDict to file
outBuff = open(OUT_FILE, "w")
for tas in iter(netIPDict):
    outBuff.write(str(tas) + "," + str(netIPDict[tas]) + "\n")
outBuff.close()
