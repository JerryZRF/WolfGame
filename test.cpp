#include <iostream>
#include <windows.h>
#include <string>
using namespace std;

int main()
{
	int n;
	cin >> n;
	string command = "start cmd /k \"D:\\Apps\\IDEA\\jdk-17.0.1\\bin\\java.exe\" -jar Server.jar --port 20210 --players " + to_string(n);
	system(command.c_str());
	Sleep(250);
	for (int i = 0; i < n; i++) {
		command = 
			"start cmd /k \"title " + to_string(i) + 
			" && \"D:\\Apps\\IDEA\\jdk-17.0.1\\bin\\java.exe\" -jar Client.jar -d --name " + 
			to_string(i) + "\"";
		cout << command << endl;
		system(command.c_str());
	}
	return 0;
}
