package com.mycompany.djl.griddb.datasets;

import ai.djl.basicdataset.tabular.utils.Feature;
import ai.djl.timeseries.dataset.FieldName;
import ai.djl.timeseries.dataset.M5Forecast;
import ai.djl.util.Progress;
import com.toshiba.mwcloud.gs.GSException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ambag
 */
public class MySQLDataset extends M5Forecast {

    private final int dataLength;
    private final File csvFile;

    protected MySQLDataset(MySQLBBuilder builder) throws GSException, FileNotFoundException {
        super(M5Forecast.builder()
                .optUsage(builder.usage)
                .optCsvFile(builder.csvFile.toPath()));
        this.csvFile = builder.csvFile;
        this.dataLength = builder.dataLength;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Progress progress) throws IOException {
        csvUrl = this.csvFile.toURI().toURL();
        super.prepare(progress);
    }

    public int getDataLength() {
        return this.dataLength;
    }

    public static Connection connectToMySQL() throws ClassNotFoundException, SQLException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String jdbcUrl = "jdbc:mysql://localhost:3306/dij";
        String username = "root";
        String password = "mysqlrootpass";
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    public static MySQLBBuilder gridDBBuilder() {
        MySQLBBuilder builder = null;
        try {
            builder = new MySQLBBuilder();
        } catch (ClassNotFoundException | SQLException ex) {
            Logger.getLogger(MySQLDataset.class.getName()).log(Level.SEVERE, null, ex);
        }
        return builder;
    }

    public static class MySQLBBuilder extends CsvBuilder {

        Connection connection;
        public int dataLength = 0;
        File csvFile;
        Usage usage;

        MySQLBBuilder() throws ClassNotFoundException, SQLException {
            connection = connectToMySQL();
        }

        @Override
        protected MySQLBBuilder self() {
            return this;
        }

        public MySQLBBuilder optStore(Connection connection) throws SQLException {
            this.connection.close();
            this.connection = connection;
            return this;
        }

        @Override
        public MySQLBBuilder addFieldFeature(FieldName name, Feature feature) {
            super.addFieldFeature(name, feature);
            return this;
        }

        @Override
        public MySQLBBuilder setTransformation(List transforms) {
            super.setTransformation(transforms);
            return this;
        }

        public MySQLBBuilder optUsage(Usage usage) {
            this.usage = usage;
            return this;
        }

        public void addFeature(String name, FieldName fieldName) {
            addFieldFeature(fieldName, new Feature(name, false));
        }

        @Override
        public MySQLBBuilder setSampling(int size, boolean isRandom) {
            super.setSampling(size, isRandom);
            return this;
        }

        @Override
        public MySQLBBuilder setContextLength(int size) {
            super.setContextLength(size);
            return this;
        }

        private File fetchDBDataAndSaveCSV(Connection con) throws Exception {
            File csvOutputFile = new File("out.csv");
            String sql = "Select * from entries";
            try ( Statement statement = con.createStatement();  ResultSet resultSet = statement.executeQuery(sql)) {
                List<String> csv = new LinkedList<>();
                while (resultSet.next()) {
                    csv.add(String.format("%s, %f", resultSet.getDate("createdAt"), resultSet.getDate("value")));
                }
                try ( PrintWriter pw = new PrintWriter(csvOutputFile)) {
                    csv.stream()
                            .forEach(pw::println);
                }
            }
            return csvOutputFile;
        }

        public MySQLBBuilder initData() throws FileNotFoundException, Exception {
            this.csvFile = fetchDBDataAndSaveCSV(this.connection);
            return this;
        }

        @Override
        public MySQLDataset build() {
            MySQLDataset gridDBDataset = null;
            try {
                gridDBDataset = new MySQLDataset(this);
            } catch (GSException | FileNotFoundException ex) {
                Logger.getLogger(MySQLDataset.class.getName()).log(Level.SEVERE, null, ex);
            }
            return gridDBDataset;
        }
    }
}
