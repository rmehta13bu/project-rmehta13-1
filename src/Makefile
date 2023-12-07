clean:
	rm -rf *.class

compile:
	javac Atm.java
	javac Bank.java


server: compile
	@echo "Enter portnumber :"
	@read -r ARGS; \
	java Bank.java $$ARGS

client: compile
	@echo "Enter hostname and portnumber separated by spaces:"
	@read -r ARGS; \
	java Atm.java $$ARGS
