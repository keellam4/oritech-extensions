#!/usr/bin/env bash
# Prints the requests in probe/classes.txt. Run by the API probe workflow after ./gradlew createMinecraftArtifacts printCompileClasspath.
set -u
src=$(find build ~/.gradle/caches -name '*-sources.jar' 2>/dev/null | grep -i neoforge | head -1)
bin=$(find build/moddev -name 'minecraft-patched-*.jar' 2>/dev/null | head -1)
echo "sources: $src"; echo "binary: $bin"
mkdir -p /tmp/src
[ -n "$src" ] && (cd /tmp/src && unzip -qo "$src")
curl -sSL -o /tmp/vf.jar https://repo.maven.apache.org/maven2/org/vineflower/vineflower/1.11.1/vineflower-1.11.1.jar
cp="$bin:$(grep '^CP ' gradle-out.txt | cut -c4- | paste -sd: -)"
jars=$(echo "$cp" | tr ':' ' ')

decompile() { # $1 = class file path inside a jar, e.g. net/minecraft/Foo.class
  for jar in $jars; do
    if unzip -l "$jar" "$1" >/dev/null 2>&1; then
      rm -rf /tmp/one /tmp/oneout; mkdir -p /tmp/one /tmp/oneout
      base=${1%.class}
      (cd /tmp/one && unzip -qo "$jar" "$base.class" "$base\$*.class" 2>/dev/null)
      java -jar /tmp/vf.jar -din=0 -rsy=1 -log=ERROR /tmp/one /tmp/oneout >/dev/null 2>&1
      find /tmp/oneout -name "$(basename "$base").java" -exec cat {} \;
      return
    fi
  done
  echo "(not found: $1)"
}

while IFS= read -r line; do
  case "$line" in ''|'#'*) continue;; esac
  kind=${line%%:*}; arg=${line#*:}
  echo "################ $line"
  case "$kind" in
    ls)
      [ -n "$src" ] && (cd /tmp/src && find "$arg" -name '*.java' 2>/dev/null | sort)
      for jar in $jars; do unzip -Z1 "$jar" "$arg*" 2>/dev/null | grep '\.class$' | grep -v '\$'; done | sort -u ;;
    find)
      found=""
      [ -n "$src" ] && found=$(cd /tmp/src && find . -name "$arg.java")
      if [ -n "$found" ]; then
        for f in $found; do echo "==== $f"; cat "/tmp/src/$f"; done
      else
        for jar in $jars; do unzip -Z1 "$jar" 2>/dev/null | grep -E "(^|/)$arg\.class$"; done | sort -u | while read -r c; do echo "==== $c"; decompile "$c"; done
      fi ;;
    src) decompile "$(echo "$arg" | tr . /).class" ;;
    grep) [ -n "$src" ] && (cd /tmp/src && grep -rEn "$arg" --include=*.java . | head -60) ;;
    javap) javap -cp "$cp" -protected "$arg" 2>&1 | head -250 ;;
  esac
done < probe/classes.txt
