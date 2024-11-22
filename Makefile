main:
	javac -cp ".." *.java */*.java

play: main
	java -cp ".." connectx.CXGame 6 7 4 connectx.Beard.Beard
test0: main
	java -Xss1g -cp ".." connectx.CXPlayerTester 4 4 4 connectx.L0.L0 connectx.Beard.Beard -v -r 1000

play1: main
	java -cp ".." connectx.CXGame 6 7 4 connectx.L1.L1 connectx.Beard.Beard 

test1: main
	java -cp ".." connectx.CXPlayerTester 6 7 4 connectx.L1.L1 connectx.Beard.Beard -v

test2: main
	java -cp ".." connectx.CXPlayerTester 6 7 4 connectx.L2.L2 connectx.Beard.Beard -v -r 100

clean:
	rm -rf */*.class
	rm -rf *.class
