package entities;
public class Player
{
    private int id;
    private String name;
    private int index;

    public Player(Integer id, Integer index, String name)
    {
        this.id = id;
        this.index = index;
        this.name = name;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getIndex() {
        return this.index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

}