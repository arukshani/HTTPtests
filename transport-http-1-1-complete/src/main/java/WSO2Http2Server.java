import org.wso2.transport.http.netty.contract.Constants;
import org.wso2.transport.http.netty.contract.HttpWsConnectorFactory;
import org.wso2.transport.http.netty.contract.ServerConnector;
import org.wso2.transport.http.netty.contract.ServerConnectorFuture;
import org.wso2.transport.http.netty.contract.config.ListenerConfiguration;
import org.wso2.transport.http.netty.contract.config.ServerBootstrapConfiguration;
import org.wso2.transport.http.netty.contractimpl.DefaultHttpWsConnectorFactory;

import java.util.HashMap;

public class WSO2Http2Server {
    private final int port;

    public WSO2Http2Server(int port) {
        this.port = port;
    }

    public static void main(String[] args)
            throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: " + WSO2Http2Server.class.getSimpleName() +
                                       " <port>"
            );
            return;
        }
        int port = Integer.parseInt(args[0]);
        new WSO2Http2Server(port).start();
    }

    public void start() throws Exception {

        HttpWsConnectorFactory connectorFactory = new DefaultHttpWsConnectorFactory();
        ListenerConfiguration listenerConfiguration = new ListenerConfiguration();
        listenerConfiguration.setPort(port);
        listenerConfiguration.setScheme(Constants.HTTP_SCHEME);
        listenerConfiguration.setVersion(Constants.HTTP_2_0);
        listenerConfiguration.setSocketIdleTimeout(10000);
        ServerConnector serverConnector = connectorFactory
                .createServerConnector(new ServerBootstrapConfiguration(new HashMap<>()), listenerConfiguration);
        ServerConnectorFuture future = serverConnector.start();
        System.out.println(WSO2Http2Server.class.getName() +
                                   " started and listening for connections on " + port);
        future.setHttpConnectorListener(new Http2ServerWaitDuringDataWrite(110000));
        future.sync();
    }
}
