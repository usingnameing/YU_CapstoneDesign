import socket
import threading

# 클라이언트 스레드 클래스
class ClientThread(threading.Thread):
    def __init__(self, clientAddress, clientSocket,clients):
        threading.Thread.__init__(self)
        self.clientSocket = clientSocket
        self.clientAddress = clientAddress
        self.clientID = clientSocket.recv(1024).decode()  # 고유 ID를 받음
        self.clients=clients

    def run(self):
        try:
            while True:
                data = self.clientSocket.recv(1024)
                if not data:  # 빈 문자열이면 연결이 끊어진 것으로 판단
                    print("Connection with ", self.clientID, " closed.")
                    break
                print("Response from ", self.clientID, ": ", data.decode())
                if self.clientID.startswith('admin'):
                    received_data=data.decode('utf-8')
                    #received_data=received_data
                    if received_data.startswith('client'):

                        part=data.split(":")
                        clientId,command=part
                        for client in self.clients:
                            #print("for")
                            if client.clientID==clientId:
                                #print("start")
                                try:
                                    client.clientSocket.sendall(command.encode('utf-8'))
                                    print(f"Sent command to {clientId}: {command}")
                                    break
                                except socket.error as e:
                                    print(f"Error occurred while sending command to client {clientId}: {e}")
                                    break
                            else:
                                print(f"No such client: {clientId}")

                    for client in self.clients:
                        if client.clientID.startswith('client'):  # for all admin clients
                            client.clientSocket.sendall(data)  # send the data
                elif self.clientID.startswith('client'):
                    for client in self.clients:
                        if client.clientID.startswith('admin'):
                            message=self.clientID+":"+data.decode('utf-8')
                            message=message.encode('utf-8')
                            length=len(message)
                            try:
                                length_byte=length.to_bytes(4,'little')
                                client.clientSocket.sendall(length_byte)
                                client.clientSocket.sendall(message)
                                print(message)
                            except socket.error as e:
                                print(f"Error occurred while sending data to client {client.clientID}: {e}")
        except Exception as e:  # 예기치 않은 에러 처리
            print(f"Error occurred with client {self.clientID}: {e}")

# 명령어 처리 스레드 클래스

def main():
    # 서버 소켓 설정
    serverSocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    serverSocket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    serverSocket.bind(("192.168.0.17", 8080))  # 모든 IP, 포트 8080에서 연결을 기다림
    print("Server started and listening for connections...")

    clients = []

    while True:
        serverSocket.listen(1)
        clientSocket, clientAddress = serverSocket.accept()

        newThread = ClientThread(clientAddress, clientSocket,clients)
        clients.append(newThread)
        newThread.start()


if __name__ == "__main__":
    main()
