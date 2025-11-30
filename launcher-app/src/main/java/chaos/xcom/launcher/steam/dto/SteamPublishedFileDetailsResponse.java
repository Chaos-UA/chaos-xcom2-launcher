package chaos.xcom.launcher.steam.dto;

import lombok.Data;

import java.util.List;

@Data
public class SteamPublishedFileDetailsResponse {
    public Response response;

    @Data
    public static class Response {
        private Integer result;
        private Integer resultcount;
        private List<PublishedFileDetails> publishedfiledetails;
    }

    @Data
    public static class PublishedFileDetails {
        private String publishedfileid;
        private Integer result;
        private String title;
        private String file_description;
        private Long time_created;
        private Long time_updated;
        private Integer creator_app_id;
        private Integer consumer_app_id;
        private List<Tag> tags;
        private Integer subscriptions;
        private Integer favorited;
        private Integer visibility;
    }

    @Data
    public static class Tag {
        private String tag;
    }
}
