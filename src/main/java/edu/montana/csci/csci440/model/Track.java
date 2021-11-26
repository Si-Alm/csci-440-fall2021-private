package edu.montana.csci.csci440.model;

import edu.montana.csci.csci440.util.DB;
import redis.clients.jedis.Jedis;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

public class Track extends Model {

    private Long trackId;
    private Long albumId;
    private Long mediaTypeId;
    private Long genreId;
    private String name;
    private String artistName;
    private String albumTitle;
    private Long milliseconds;
    private Long bytes;
    private BigDecimal unitPrice;
    private static long count = 0;

    public static final String REDIS_CACHE_KEY = "cs440-tracks-count-cache";

    public Track() {
        mediaTypeId = 1l;
        genreId = 1l;
        milliseconds  = 0l;
        bytes  = 0l;
        unitPrice = new BigDecimal("0");
    }

    protected Track(ResultSet results) throws SQLException {
        name = results.getString("Name");
        milliseconds = results.getLong("Milliseconds");
        bytes = results.getLong("Bytes");
        unitPrice = results.getBigDecimal("UnitPrice");
        trackId = results.getLong("TrackId");
        albumId = results.getLong("AlbumId");
        mediaTypeId = results.getLong("MediaTypeId");
        genreId = results.getLong("GenreId");
        artistName = getAlbum().getArtist().getName();
        albumTitle = getAlbum().getTitle();
    }

    public static Track find(long i) {
        try (Connection conn = DB.connect();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM tracks WHERE TrackId=?")) {
            stmt.setLong(1, i);
            ResultSet results = stmt.executeQuery();
            if (results.next()) {
                return new Track(results);
            } else {
                return null;
            }
        } catch (SQLException sqlException) {
            throw new RuntimeException(sqlException);
        }
    }

    public static Long count() {
        Jedis redisClient = new Jedis(); // use this class to access redis and create a cache
        String redisCache = redisClient.get(REDIS_CACHE_KEY);

        if(null == redisCache || count != Integer.parseInt(redisCache)) {
            try (Connection conn = DB.connect();
                 PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) as Count FROM tracks")) {
                ResultSet results = stmt.executeQuery();
                redisCache = String.valueOf(results.getLong("Count"));
                redisClient.set(REDIS_CACHE_KEY, redisCache);
                count = Integer.parseInt(redisCache);
                if (results.next()) {
                    return results.getLong("Count");
                } else {
                    throw new IllegalStateException("Should find a count!");
                }
            } catch (SQLException sqlException) {
                throw new RuntimeException(sqlException);
            }
        }

        return Long.parseLong(redisClient.get(REDIS_CACHE_KEY));
    }

    public Album getAlbum() {
        return Album.find(albumId);
    }
    public MediaType getMediaType() {
        return null;
    }
    public Genre getGenre() {
        return null;
    }


    public List<Playlist> getPlaylists(){
        return Playlist.forTracks(this.trackId);
    }

    public Long getTrackId() {
        return trackId;
    }

    public void setTrackId(Long trackId) {
        this.trackId = trackId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getMilliseconds() {
        return milliseconds;
    }

    public void setMilliseconds(Long milliseconds) {
        this.milliseconds = milliseconds;
    }

    public Long getBytes() {
        return bytes;
    }

    public void setBytes(Long bytes) {
        this.bytes = bytes;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Long getAlbumId() {
        return albumId;
    }

    public void setAlbumId(Long albumId) {
        this.albumId = albumId;
    }

    public void setAlbum(Album album) {
        albumId = album.getAlbumId();
    }

    public Long getMediaTypeId() {
        return mediaTypeId;
    }

    public void setMediaTypeId(Long mediaTypeId) {
        this.mediaTypeId = mediaTypeId;
    }

    public Long getGenreId() {
        return genreId;
    }

    public void setGenreId(Long genreId) {
        this.genreId = genreId;
    }


    @Override
    public boolean verify() {
        _errors.clear();

        if(name == null || "".equals(name)) {
            addError("Name can't be null or blank!");
        }

        if(albumId == null || 0 == albumId) {
            addError("Track must have an Album ID!");
        }

        return !hasErrors();
    }

    @Override
    public boolean create() {
        if(verify()) {
            try(Connection conn = DB.connect();
                PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO tracks (AlbumId, MediaTypeId, GenreId, Name, Milliseconds, Bytes, UnitPrice) " +
                                         "VALUES  (?,?,?,?,?,?,?);")) {

                stmt.setLong(1, this.albumId);
                stmt.setLong(2, this.mediaTypeId);
                stmt.setLong(3, this.genreId);
                stmt.setString(4, this.name);
                stmt.setLong(5, this.milliseconds);
                stmt.setLong(6, this.bytes);
                stmt.setBigDecimal(7, this.unitPrice);
                count++;
                this.trackId = DB.getLastID(conn);

                return true;
            } catch(SQLException sqlException) {
                throw new RuntimeException(sqlException);
            }
        } else {
            return false;
        }
    }

    @Override
    public boolean update() {
        if(verify()) {
            try (Connection conn = DB.connect();
                 PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE tracks SET Name = ? WHERE TrackId = ?")) {


                stmt.setString(1, this.name);
                stmt.setLong(2, this.trackId);
                stmt.executeUpdate();
                return true;
            } catch (SQLException sqlException) {
                throw new RuntimeException(sqlException);
            }
        } else {
            return false;
        }
    }

    @Override
    public void delete() {
        try (Connection conn = DB.connect();
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM tracks WHERE TrackId=?")) {
            stmt.setLong(1, this.trackId);
            stmt.executeUpdate();
        } catch (SQLException sqlException) {
            throw new RuntimeException(sqlException);
        }
    }

    public String getArtistName() {
        // TODO implement more efficiently
        //  hint: cache on this model object
        return artistName;
    }

    public String getAlbumTitle() {
        // TODO implement more efficiently
        //  hint: cache on this model object
        return albumTitle;
    }

    public static List<Track> advancedSearch(int page, int count,
                                             String search, Integer artistId, Integer albumId,
                                             Integer maxRuntime, Integer minRuntime) {
        LinkedList<Object> args = new LinkedList<>();

        String query = "SELECT * FROM tracks " +
                "JOIN albums ON tracks.AlbumId = albums.AlbumId " +
                "WHERE name LIKE ?";
        args.add("%" + search + "%");

        // Conditionally include the query and argument
        if (artistId != null) {
            query += " AND albums.ArtistId=? ";
            args.add(artistId);
        }

        if(null != albumId) {
            query += " AND tracks.AlbumId = ?";
            args.add(albumId);
        }

        if(null != maxRuntime) {
            query += " AND Milliseconds <= ?";
            args.add(1000 * maxRuntime);
        }

        if(null != minRuntime) {
            query += " AND Milliseconds >= ?";
            args.add(1000 * minRuntime);
        }

        query += " ORDER BY TrackId";
        query += " LIMIT ? OFFSET ? ";
        args.add(count);
        args.add(count * page - count);

        try (Connection conn = DB.connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            for (int i = 0; i < args.size(); i++) {
                Object arg = args.get(i);
                stmt.setObject(i + 1, arg);
            }
            ResultSet results = stmt.executeQuery();
            List<Track> resultList = new LinkedList<>();
            while (results.next()) {
                resultList.add(new Track(results));
            }
            return resultList;
        } catch (SQLException sqlException) {
            throw new RuntimeException(sqlException);
        }
    }

    public static List<Track> search(int page, int count, String orderBy, String search) {
        String query = "SELECT * FROM tracks WHERE name LIKE ? ORDER BY " + orderBy + " LIMIT ? OFFSET";
        search = "%" + search + "%";
        try (Connection conn = DB.connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, search);
            stmt.setString(2, orderBy);
            stmt.setInt(3, count);
            stmt.setInt(4, page * count - count);
            ResultSet results = stmt.executeQuery();
            List<Track> resultList = new LinkedList<>();
            while (results.next()) {
                resultList.add(new Track(results));
            }
            return resultList;
        } catch (SQLException sqlException) {
            throw new RuntimeException(sqlException);
        }
    }

    public static List<Track> forAlbum(Long albumId) {
        String query = "SELECT * FROM tracks WHERE AlbumId=?";
        try (Connection conn = DB.connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setLong(1, albumId);
            ResultSet results = stmt.executeQuery();
            List<Track> resultList = new LinkedList<>();
            while (results.next()) {
                resultList.add(new Track(results));
            }
            return resultList;
        } catch (SQLException sqlException) {
            throw new RuntimeException(sqlException);
        }
    }

    public static List<Track> forPlaylist(Long playlistId) {
        String query = "SELECT * FROM tracks" +
                " JOIN playlist_track ON tracks.TrackId = playlist_track.TrackId" +
                " WHERE playlist_track.PlaylistId = ?" +
                " ORDER BY tracks.Name";
        try (Connection conn = DB.connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setLong(1, playlistId);
            ResultSet results = stmt.executeQuery();
            List<Track> resultList = new LinkedList<>();
            while (results.next()) {
                resultList.add(new Track(results));
            }
            return resultList;
        } catch (SQLException sqlException) {
            throw new RuntimeException(sqlException);
        }
    }

    // Sure would be nice if java supported default parameter values
    public static List<Track> all() {
        return all(0, Integer.MAX_VALUE);
    }

    public static List<Track> all(int page, int count) {
        return all(page, count, "TrackId");
    }

    public static List<Track> all(int page, int count, String orderBy) {

        try (Connection conn = DB.connect();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM tracks " +
                     "ORDER BY " + orderBy + " LIMIT ? OFFSET ?"
             )) {
            //stmt.setString(1, orderBy); <-- wouldn't work with statement method?
            stmt.setInt(1, count);
            stmt.setInt(2, count * page - count);

            ResultSet results = stmt.executeQuery();
            List<Track> resultList = new LinkedList<>();
            while (results.next()) {
                resultList.add(new Track(results));
            }
            return resultList;
        } catch (SQLException sqlException) {
            throw new RuntimeException(sqlException);
        }
    }
}
